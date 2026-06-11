/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.am.analytics.publisher.reporter.moesif.retry;

import com.moesif.api.MoesifAPIClient;
import com.moesif.api.controllers.APIController;
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import com.moesif.api.http.response.HttpResponse;
import com.moesif.api.models.EventModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.util.HttpStatusHelper;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-Moesif-key bounded retry buffer with a circuit-breaker style probe.
 *
 * When a publish fails with a retryable status (5xx / 408 / 429 / transport), the failing batch is stashed
 * here and the buffer flips to unhealthy. While unhealthy, new batches are stashed directly without hitting
 * Moesif. A scheduled probe periodically tries to drain the oldest batch; on success the buffer flips back
 * to healthy and an opportunistic burst drains additional batches before falling back to the regular cadence.
 *
 * Bounded by event count (not batch count) since callers don't know the internal batch size. When the cap
 * would be exceeded, the oldest batches are evicted (drop-oldest FIFO).
 *
 * Thread-safety: all deque + counter mutations are guarded by a single lock. SDK calls happen outside the
 * lock. Only one probe is in flight at a time (guarded by a semaphore). Throttled-log timestamps and the
 * healthy flag use CAS so concurrent SDK callbacks never double-log.
 */
public class MoesifRetryBuffer {
    private static final Logger log = LogManager.getLogger(MoesifRetryBuffer.class);

    private final String moesifKey;
    private final APIController api;
    private final int capacityEvents;
    private final long intervalMs;
    private final long throttleWindowNanos;
    private final int drainBurstSize;
    private final long drainBatchDelayMs;
    private final java.util.concurrent.ScheduledExecutorService scheduler;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<List<EventModel>> stash = new ArrayDeque<>();
    private int currentEventCount;

    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicLong lastFailureLogAtNanos = new AtomicLong(0L);
    private final AtomicLong unhealthySinceNanos = new AtomicLong(0L);
    private final AtomicLong totalAttemptsWhileUnhealthy = new AtomicLong(0L);
    private final AtomicLong droppedDueToFull = new AtomicLong(0L);
    private final AtomicInteger inflightBurstSteps = new AtomicInteger(0);
    private final Semaphore probeInFlight = new Semaphore(1);

    public MoesifRetryBuffer(String moesifKey, MoesifAPIClient client, RetryConfig config) {
        this.moesifKey = moesifKey;
        this.api = client.getAPI();
        this.capacityEvents = config.getBufferSize();
        this.intervalMs = TimeUnit.SECONDS.toMillis(config.getIntervalSeconds());
        this.throttleWindowNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs * config.getLogMultiplier());
        this.drainBurstSize = config.getDrainBurstSize();
        this.drainBatchDelayMs = config.getDrainBatchDelayMs();
        this.scheduler = config.getScheduler();
        this.scheduler.scheduleAtFixedRate(this::safeProbe, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    /**
     * Adds a batch to the buffer. Evicts oldest batches (FIFO) if the cap would be exceeded.
     * If a single batch is larger than the entire capacity, it is dropped entirely.
     */
    public void stash(List<EventModel> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (batch.size() > capacityEvents) {
            droppedDueToFull.addAndGet(batch.size());
            log.warn("Cannot queue {} analytics events at once (max {} per Moesif key); these events are dropped",
                    batch.size(), capacityEvents);
            return;
        }
        lock.lock();
        try {
            while (currentEventCount + batch.size() > capacityEvents && !stash.isEmpty()) {
                List<EventModel> evicted = stash.pollFirst();
                currentEventCount -= evicted.size();
                droppedDueToFull.addAndGet(evicted.size());
            }
            stash.addLast(batch);
            currentEventCount += batch.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called by the client's normal-path callback when a send succeeds. No-op when already healthy.
     * On recovery, schedules a burst drain to flush stashed events faster than the regular cadence.
     */
    public void onSuccess() {
        if (healthy.compareAndSet(false, true)) {
            long downForMs = (System.nanoTime() - unhealthySinceNanos.get()) / 1_000_000L;
            int buffered;
            lock.lock();
            try {
                buffered = currentEventCount;
            } finally {
                lock.unlock();
            }
            log.info("Moesif (key ...{}) is reachable again after {} ms and {} attempts. "
                            + "Sending {} queued analytics events.",
                    keyTail(), downForMs, totalAttemptsWhileUnhealthy.get(), buffered);
            lastFailureLogAtNanos.set(0L);
            totalAttemptsWhileUnhealthy.set(0L);
            scheduleBurstStep();
        }
    }

    /**
     * Called by the client's normal-path callback when a send fails with a retryable error.
     * Stashes the batch (so caller can drop the reference), flips to unhealthy on first transition,
     * and emits a throttled log.
     */
    public void onRetryableFailure(List<EventModel> batch) {
        recordFailureTransition();
        stash(batch);
        logFailureThrottled();
    }

    private void recordFailureTransition() {
        if (healthy.compareAndSet(true, false)) {
            unhealthySinceNanos.set(System.nanoTime());
            int buffered;
            lock.lock();
            try {
                buffered = currentEventCount;
            } finally {
                lock.unlock();
            }
            log.error("Cannot reach Moesif (key ...{}). Queueing analytics events for retry ({}/{} events queued).",
                    keyTail(), buffered, capacityEvents);
            lastFailureLogAtNanos.set(System.nanoTime());
        }
    }

    private void logFailureThrottled() {
        totalAttemptsWhileUnhealthy.incrementAndGet();
        long now = System.nanoTime();
        long prev = lastFailureLogAtNanos.get();
        if (prev != 0L && now - prev < throttleWindowNanos) {
            return;
        }
        if (!lastFailureLogAtNanos.compareAndSet(prev, now)) {
            return;
        }
        long downForMs = (now - unhealthySinceNanos.get()) / 1_000_000L;
        int buffered;
        lock.lock();
        try {
            buffered = currentEventCount;
        } finally {
            lock.unlock();
        }
        log.error("Moesif (key ...{}) still unreachable after {} ms. Queued: {}/{} events, "
                        + "dropped {} events so far, retry attempts: {}",
                keyTail(), downForMs, buffered, capacityEvents,
                droppedDueToFull.get(), totalAttemptsWhileUnhealthy.get());
    }

    /**
     * Restores a batch to the front of the queue after a probe send failure. Does not evict —
     * temporary overshoot is acceptable; subsequent {@link #stash} calls will re-balance.
     */
    private void restoreFront(List<EventModel> batch) {
        lock.lock();
        try {
            stash.addFirst(batch);
            currentEventCount += batch.size();
        } finally {
            lock.unlock();
        }
    }

    private List<EventModel> pollOldest() {
        lock.lock();
        try {
            List<EventModel> batch = stash.pollFirst();
            if (batch != null) {
                currentEventCount -= batch.size();
            }
            return batch;
        } finally {
            lock.unlock();
        }
    }

    private void safeProbe() {
        try {
            probe();
        } catch (Throwable t) {
            log.error("Periodic Moesif retry check failed (key ...{})", keyTail(), t);
        }
    }

    private void probe() {
        if (healthy.get()) {
            lock.lock();
            try {
                if (stash.isEmpty()) {
                    return;
                }
            } finally {
                lock.unlock();
            }
        }
        if (!probeInFlight.tryAcquire()) {
            return;
        }
        List<EventModel> batch = pollOldest();
        if (batch == null) {
            probeInFlight.release();
            return;
        }
        sendBatch(batch, false);
    }

    private void scheduleBurstStep() {
        if (inflightBurstSteps.incrementAndGet() > drainBurstSize) {
            inflightBurstSteps.decrementAndGet();
            return;
        }
        scheduler.schedule(this::safeBurstStep, drainBatchDelayMs, TimeUnit.MILLISECONDS);
    }

    private void safeBurstStep() {
        try {
            burstStep();
        } catch (Throwable t) {
            inflightBurstSteps.decrementAndGet();
            log.error("Moesif catch-up send failed (key ...{})", keyTail(), t);
        }
    }

    private void burstStep() {
        if (!healthy.get()) {
            inflightBurstSteps.decrementAndGet();
            return;
        }
        List<EventModel> batch = pollOldest();
        if (batch == null) {
            inflightBurstSteps.decrementAndGet();
            return;
        }
        if (!probeInFlight.tryAcquire()) {
            restoreFront(batch);
            inflightBurstSteps.decrementAndGet();
            return;
        }
        sendBatch(batch, true);
    }

    private void sendBatch(List<EventModel> batch, boolean fromBurst) {
        APICallBack<HttpResponse> callback = new APICallBack<HttpResponse>() {
            @Override
            public void onSuccess(HttpContext context, HttpResponse response) {
                try {
                    int code = context.getResponse().getStatusCode();
                    if (HttpStatusHelper.isSuccess(code)) {
                        MoesifRetryBuffer.this.onSuccess();
                        if (fromBurst) {
                            scheduleBurstStep();
                        }
                    } else if (HttpStatusHelper.shouldRetry(code)) {
                        restoreFront(batch);
                        recordFailureTransition();
                        logFailureThrottled();
                    } else {
                        log.error("Moesif rejected {} queued analytics events with status {} (key ...{}); "
                                        + "these events will not be retried",
                                batch.size(), code, keyTail());
                    }
                } catch (Throwable t) {
                    restoreFront(batch);
                    log.error("Unexpected error after retrying queued analytics events (key ...{})", keyTail(), t);
                } finally {
                    if (fromBurst) {
                        inflightBurstSteps.decrementAndGet();
                    }
                    probeInFlight.release();
                }
            }

            @Override
            public void onFailure(HttpContext context, Throwable error) {
                try {
                    restoreFront(batch);
                    recordFailureTransition();
                    logFailureThrottled();
                } catch (Throwable t) {
                    log.error("Unexpected error while handling Moesif retry failure (key ...{})", keyTail(), t);
                } finally {
                    if (fromBurst) {
                        inflightBurstSteps.decrementAndGet();
                    }
                    probeInFlight.release();
                }
            }
        };
        try {
            api.createEventsBatchAsync(batch, callback);
        } catch (IOException e) {
            restoreFront(batch);
            recordFailureTransition();
            logFailureThrottled();
            if (fromBurst) {
                inflightBurstSteps.decrementAndGet();
            }
            probeInFlight.release();
        }
    }

    private String keyTail() {
        if (moesifKey == null || moesifKey.length() <= 4) {
            return "****";
        }
        return moesifKey.substring(moesifKey.length() - 4);
    }
}
