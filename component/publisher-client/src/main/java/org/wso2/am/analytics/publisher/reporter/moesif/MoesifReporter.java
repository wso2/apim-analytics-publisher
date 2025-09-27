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
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.Map;
import java.util.Timer;

/**
 * Moesif Metric Reporter Implementation. This implementation is responsible for sending analytics data into Moesif
 * dashboard in a secure and reliable way.
 */
public class MoesifReporter extends AbstractMetricReporter {
    private static final Logger log = LogManager.getLogger(MoesifReporter.class);
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
        if (properties.get(Constants.TYPE).equals(Constants.MOESIF)) {
            String moesifKey = properties.get(Constants.MOESIF_KEY);
            String moesifBasePath = properties.get(Constants.MOESIF_BASE_URL);
            if (moesifBasePath == null || moesifBasePath.isEmpty()) {
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

            this.eventQueue = new EventQueue(queueSize, workerThreads, keyRetriever);

            MissedEventHandler missedEventHandler = new MissedEventHandler(keyRetriever);
            // execute MissedEventHandler periodically.
            Timer timer = new Timer();
            timer.schedule(missedEventHandler, 0, MoesifMicroserviceConstants.PERIODIC_CALL_DELAY);
        }
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

