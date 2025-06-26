/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.reporter.moesif;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.wso2.am.analytics.publisher.client.AbstractMoesifClient;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Will dequeue the events from queues and send then to the moesif client.
 */
public class ParallelQueueWorker implements Runnable {
    private static final Logger log = LogManager.getLogger(ParallelQueueWorker.class);
    private BlockingQueue<MetricEventBuilder> eventQueue;
    private AbstractMoesifClient client;
    private final int batchSize = 50;
    private final long batchTimeoutMs = 2000;

    public ParallelQueueWorker(BlockingQueue<MetricEventBuilder> queue, AbstractMoesifClient moesifClient) {
        this.eventQueue = queue;
        this.client = moesifClient;
    }

    /**
     * Continuously runs a worker thread that processes analytics events in batches from a blocking queue.
     * <p>
     * The method polls the {@code eventQueue} at fixed intervals (100ms) for new {@code MetricEventBuilder} events.
     * Events are collected into a batch and processed when either:
     * <ul>
     *   <li>The batch reaches the configured {@code batchSize}</li>
     *   <li>The elapsed time since the last batch processing exceeds {@code batchTimeoutMs}</li>
     * </ul>
     * Once either condition is met, the collected batch is sent using {@code processBatch}, and the timer is reset.
     * <p>
     * If the thread is interrupted, it exits gracefully by setting the interrupt flag. Any other exception
     * during processing is logged, and the affected events will be dropped.
     *
     * This method is intended to be executed in a dedicated thread via an {@code ExecutorService} or similar mechanism.
     */

    public void run() {
        List<MetricEventBuilder> batch = new ArrayList<>(batchSize);
        long lastBatchTime = System.currentTimeMillis();
        while (true) {
            MetricEventBuilder eventBuilder;
            try {

                eventBuilder = eventQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (eventBuilder != null) {
                    batch.add(eventBuilder);
                }

                long currrentBatchTime = System.currentTimeMillis();
                boolean shouldSendBatch = batch.size() >= batchSize ||
                        (currrentBatchTime - lastBatchTime) >= batchTimeoutMs;

                if (shouldSendBatch) {
                    log.debug("Sending batch of {} events", batch.size());
                    processBatch(new ArrayList<>(batch));
                    batch.clear();
                    lastBatchTime = currrentBatchTime;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Analytics event sending failed. Event will be dropped", e);
            }
        }
    }
    private void processBatch(List<MetricEventBuilder> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            if (batch.size() == 1) {
                client.publish(batch.get(0));
            } else {
                client.publishBatch(batch);
            }
            log.debug("Successfully processed batch of {} events", batch.size());
        } catch (MetricReportingException e) {
            log.error("Failed to process batch of {} events", batch.size(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing batch", e);
        }
    }



}
