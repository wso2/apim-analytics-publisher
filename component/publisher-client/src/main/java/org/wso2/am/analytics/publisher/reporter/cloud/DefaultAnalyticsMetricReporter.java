/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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

package org.wso2.am.analytics.publisher.reporter.cloud;

import org.wso2.am.analytics.publisher.client.EventHubClient;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricReporter;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.reporter.TimerMetric;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.List;
import java.util.Map;

/**
 * Choreo Metric Reporter Implementation. This implementation is responsible for sending analytics data into Choreo
 * cloud in a secure and reliable way.
 */
public class DefaultAnalyticsMetricReporter extends AbstractMetricReporter {
    private EventQueue eventQueue;

    public DefaultAnalyticsMetricReporter(Map<String, String> properties) throws MetricCreationException {
        super(properties);
        int queueSize = 20000;
        int workerThreads = 5;
        if (properties.get("queue.size") != null) {
            queueSize = Integer.parseInt(properties.get("queue.size"));
        }
        if (properties.get("worker.thread.count") != null) {
            workerThreads = Integer.parseInt(properties.get("worker.thread.count"));
        }
        eventQueue = new EventQueue(queueSize, workerThreads,
                                    new EventHubClient(getConfiguration().get(Constants.SAS_TOKEN)));
    }

    @Override
    protected void validateConfigProperties(Map<String, String> properties) throws MetricCreationException {
        if (properties != null) {
            List<String> requiredProperties = DefaultInputValidator.getInstance().getConfigProperties();
            for (String property : requiredProperties) {
                if (properties.get(property) == null || properties.get(property).isEmpty()) {
                    throw new MetricCreationException(property + " is missing in config data");
                }
            }
        } else {
            throw new MetricCreationException("Configuration properties cannot be null");
        }
    }

    @Override
    protected CounterMetric createCounter(String name, MetricSchema schema) {
        DefaultCounterMetric counterMetric = new DefaultCounterMetric(name, eventQueue, schema);
        return counterMetric;
    }

    @Override
    protected TimerMetric createTimer(String name) {
        return null;
    }

}
