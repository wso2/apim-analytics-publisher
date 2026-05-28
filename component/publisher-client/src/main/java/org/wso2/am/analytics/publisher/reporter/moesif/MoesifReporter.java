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
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricReporter;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.reporter.TimerMetric;
import org.wso2.am.analytics.publisher.reporter.cloud.DefaultAnalyticsThreadFactory;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.RetryConfig;
import org.wso2.am.analytics.publisher.reporter.moesif.sampling.MoesifSamplingManager;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Moesif Metric Reporter Implementation. This implementation is responsible for sending analytics data into Moesif
 * dashboard in a secure and reliable way.
 */
public class MoesifReporter extends AbstractMetricReporter {
    private static final Logger log = LogManager.getLogger(MoesifReporter.class);
    private static final int SHARED_SCHEDULER_THREADS = 2;
    private final EventQueue eventQueue;

    public MoesifReporter(Map<String, String> properties) throws MetricCreationException {
        super(properties);
        int queueSize = Constants.DEFAULT_QUEUE_SIZE;
        int workerThreads = Constants.DEFAULT_WORKER_THREADS;
        if (properties.get(Constants.QUEUE_SIZE) != null) {
            queueSize = Integer.parseInt(properties.get(Constants.QUEUE_SIZE));
        }
        if (properties.get(Constants.WORKER_THREAD_COUNT) != null) {
            workerThreads = Integer.parseInt(properties.get(Constants.WORKER_THREAD_COUNT));
        }
        ScheduledExecutorService sharedScheduler = Executors.newScheduledThreadPool(SHARED_SCHEDULER_THREADS,
                new DefaultAnalyticsThreadFactory("Moesif-Shared-Scheduler"));
        MoesifSamplingManager samplingManager = buildSamplingManager(properties);
        RetryConfig retryConfig = buildRetryConfig(properties, sharedScheduler);
        if (properties.get(Constants.TYPE).contains(Constants.MOESIF)) {
            String moesifKey = properties.get(Constants.MOESIF_KEY);
            String moesifBasePath = properties.get(Constants.MOESIF_BASE_URL);
            if (retryConfig != null || (samplingManager != null && samplingManager.isEnabled())) {
                this.eventQueue = new EventQueue(queueSize, workerThreads, moesifKey, moesifBasePath,
                        samplingManager, retryConfig);
            } else if (moesifBasePath == null || moesifBasePath.isEmpty()) {
                this.eventQueue = new EventQueue(queueSize, workerThreads, moesifKey);
            } else {
                this.eventQueue = new EventQueue(queueSize, workerThreads, moesifKey, moesifBasePath);
            }
        } else {
            String moesifBasePath = properties.get(
                    MoesifMicroserviceConstants.MOESIF_PROTOCOL_WITH_FQDN_KEY) + properties.get(
                    MoesifMicroserviceConstants.MOESIF_MS_VERSIONING_KEY);
            MoesifKeyRetriever keyRetriever = MoesifKeyRetriever.getInstance(
                    properties.get(MoesifMicroserviceConstants.MS_USERNAME_CONFIG_KEY),
                    properties.get(MoesifMicroserviceConstants.MS_PWD_CONFIG_KEY), moesifBasePath);

            if (retryConfig != null) {
                this.eventQueue = new EventQueue(queueSize, workerThreads, keyRetriever, retryConfig);
            } else {
                this.eventQueue = new EventQueue(queueSize, workerThreads, keyRetriever);
            }

            MissedEventHandler missedEventHandler = new MissedEventHandler(keyRetriever);
            // execute MissedEventHandler periodically.
            Timer timer = new Timer();
            timer.schedule(missedEventHandler, 0, MoesifMicroserviceConstants.PERIODIC_CALL_DELAY);
        }
    }

    private RetryConfig buildRetryConfig(Map<String, String> properties, ScheduledExecutorService scheduler) {
        boolean enabled = Boolean.parseBoolean(properties.getOrDefault(
                MoesifMicroserviceConstants.RETRY_BUFFER_ENABLED_KEY,
                String.valueOf(MoesifMicroserviceConstants.DEFAULT_RETRY_BUFFER_ENABLED)));
        if (!enabled) {
            return null;
        }
        int bufferSize = parseIntOrDefault(properties.get(MoesifMicroserviceConstants.RETRY_BUFFER_SIZE_KEY),
                MoesifMicroserviceConstants.DEFAULT_RETRY_BUFFER_SIZE,
                MoesifMicroserviceConstants.RETRY_BUFFER_SIZE_KEY);
        long intervalSeconds = parseLongOrDefault(
                properties.get(MoesifMicroserviceConstants.RETRY_INTERVAL_SECONDS_KEY),
                MoesifMicroserviceConstants.DEFAULT_RETRY_INTERVAL_SECONDS,
                MoesifMicroserviceConstants.RETRY_INTERVAL_SECONDS_KEY);
        int logMultiplier = parseIntOrDefault(properties.get(MoesifMicroserviceConstants.RETRY_LOG_MULTIPLIER_KEY),
                MoesifMicroserviceConstants.DEFAULT_RETRY_LOG_MULTIPLIER,
                MoesifMicroserviceConstants.RETRY_LOG_MULTIPLIER_KEY);
        int drainBurstSize = parseIntOrDefault(properties.get(MoesifMicroserviceConstants.RETRY_DRAIN_BURST_SIZE_KEY),
                MoesifMicroserviceConstants.DEFAULT_RETRY_DRAIN_BURST_SIZE,
                MoesifMicroserviceConstants.RETRY_DRAIN_BURST_SIZE_KEY);
        long drainBatchDelayMs = parseLongOrDefault(
                properties.get(MoesifMicroserviceConstants.RETRY_DRAIN_BATCH_DELAY_MS_KEY),
                MoesifMicroserviceConstants.DEFAULT_RETRY_DRAIN_BATCH_DELAY_MS,
                MoesifMicroserviceConstants.RETRY_DRAIN_BATCH_DELAY_MS_KEY);
        log.info("Moesif retry queue enabled (capacity={} events per Moesif key, check interval={}s, "
                        + "log every {} checks, catch-up burst={} sends with {}ms delay between sends)",
                bufferSize, intervalSeconds, logMultiplier, drainBurstSize, drainBatchDelayMs);
        return new RetryConfig(bufferSize, intervalSeconds, logMultiplier, drainBurstSize, drainBatchDelayMs,
                scheduler);
    }

    private int parseIntOrDefault(String value, int fallback, String key) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value, using default {}", key, fallback);
            return fallback;
        }
    }

    private long parseLongOrDefault(String value, long fallback, String key) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value, using default {}", key, fallback);
            return fallback;
        }
    }

    private MoesifSamplingManager buildSamplingManager(Map<String, String> properties) {
        boolean enabled = Boolean.parseBoolean(
                properties.getOrDefault(MoesifMicroserviceConstants.SAMPLING_ENABLED_KEY, "false"));
        if (!enabled) {
            return null;
        }
        long refreshInterval = MoesifMicroserviceConstants.DEFAULT_SAMPLING_REFRESH_INTERVAL_MS;
        int fallbackRate = MoesifMicroserviceConstants.DEFAULT_SAMPLING_FALLBACK_RATE;
        try {
            String refresh = properties.get(MoesifMicroserviceConstants.SAMPLING_REFRESH_INTERVAL_KEY);
            if (refresh != null) {
                refreshInterval = Long.parseLong(refresh);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value, using default {}ms",
                    MoesifMicroserviceConstants.SAMPLING_REFRESH_INTERVAL_KEY, refreshInterval);
        }
        try {
            String rate = properties.get(MoesifMicroserviceConstants.SAMPLING_FALLBACK_RATE_KEY);
            if (rate != null) {
                fallbackRate = Integer.parseInt(rate);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value, using default {}",
                    MoesifMicroserviceConstants.SAMPLING_FALLBACK_RATE_KEY, fallbackRate);
        }
        return new MoesifSamplingManager(true, refreshInterval, fallbackRate);
    }

    @Override
    protected void validateConfigProperties(Map<String, String> map) throws MetricCreationException {

    }

    @Override
    public CounterMetric createCounter(String name, MetricSchema metricSchema) throws MetricCreationException {
        MoesifCounterMetric counterMetric = new MoesifCounterMetric(name, eventQueue, metricSchema);
        return counterMetric;
    }

    @Override
    protected TimerMetric createTimer(String s) {
        return null;
    }
}

