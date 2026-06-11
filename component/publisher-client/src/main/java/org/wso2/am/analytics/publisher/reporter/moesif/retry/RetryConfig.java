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

import java.util.concurrent.ScheduledExecutorService;

/**
 * Immutable configuration for the Moesif retry buffer. Built once by the reporter and shared across all
 * per-key {@link MoesifRetryBuffer} instances.
 */
public final class RetryConfig {
    private final int bufferSize;
    private final long intervalSeconds;
    private final int logMultiplier;
    private final int drainBurstSize;
    private final long drainBatchDelayMs;
    private final ScheduledExecutorService scheduler;

    public RetryConfig(int bufferSize, long intervalSeconds, int logMultiplier,
                       int drainBurstSize, long drainBatchDelayMs, ScheduledExecutorService scheduler) {
        this.bufferSize = bufferSize;
        this.intervalSeconds = intervalSeconds;
        this.logMultiplier = logMultiplier;
        this.drainBurstSize = drainBurstSize;
        this.drainBatchDelayMs = drainBatchDelayMs;
        this.scheduler = scheduler;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getLogMultiplier() {
        return logMultiplier;
    }

    public int getDrainBurstSize() {
        return drainBurstSize;
    }

    public long getDrainBatchDelayMs() {
        return drainBatchDelayMs;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
