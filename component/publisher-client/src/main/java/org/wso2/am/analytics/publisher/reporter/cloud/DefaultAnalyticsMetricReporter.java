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

import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpRetryOptions;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.client.EventHubClient;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricReporter;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.reporter.TimerMetric;
import org.wso2.am.analytics.publisher.util.Constants;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Choreo Metric Reporter Implementation. This implementation is responsible for sending analytics data into Choreo
 * cloud in a secure and reliable way.
 */
public class DefaultAnalyticsMetricReporter extends AbstractMetricReporter {
    private static final Logger log = Logger.getLogger(DefaultAnalyticsMetricReporter.class);
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
        String authToken = properties.get(Constants.AUTH_API_TOKEN);
        String authEndpoint = properties.get(Constants.AUTH_API_URL);
        AmqpRetryOptions retryOptions = createRetryOptions(properties);
        EventHubClient client = new EventHubClient(authEndpoint, authToken, retryOptions);
        eventQueue = new EventQueue(queueSize, workerThreads, client);
    }

    private AmqpRetryOptions createRetryOptions(Map<String, String> properties) {
        int maxRetries = 2;
        int delay = 30;
        int maxDelay = 120;
        int tryTimeout = 60;
        AmqpRetryMode retryMode = AmqpRetryMode.FIXED;
        if (properties.get("eventhub.client.max.retries") != null) {
            int tempMaxRetries = Integer.parseInt(properties.get("eventhub.client.max.retries"));
            if (tempMaxRetries > 0) {
                maxRetries = tempMaxRetries;
            } else {
                log.warn("Provided 'eventhub.client.max.retries' value is less than 0 and not acceptable. Hence using"
                                 + " the default value.");
            }
        }
        if (properties.get("eventhub.client.delay") != null) {
            int tempDelay = Integer.parseInt(properties.get("eventhub.client.delay"));
            if (tempDelay > 0) {
                delay = tempDelay;
            } else {
                log.warn("Provided 'eventhub.client.delay' value is less than 0 and not acceptable. Hence using"
                                 + " the default value.");
            }
        }
        if (properties.get("eventhub.client.max.delay") != null) {
            int tempMaxDelay = Integer.parseInt(properties.get("eventhub.client.max.delay"));
            if (tempMaxDelay > 0) {
                maxDelay = tempMaxDelay;
            } else {
                log.warn("Provided 'eventhub.client.max.delay' value is less than 0 and not acceptable. Hence using"
                                 + " the default value.");
            }
        }
        if (properties.get("eventhub.client.try.timeout") != null) {
            int tempTryTimeout = Integer.parseInt(properties.get("eventhub.client.try.timeout"));
            if (tempTryTimeout > 0) {
                tryTimeout = tempTryTimeout;
            } else {
                log.warn("Provided 'eventhub.client.try.timeout' value is less than 0 and not acceptable. Hence using"
                                 + " the default value.");
            }
        }
        if (properties.get("eventhub.client.retry.mode") != null) {
            String tempRetryMode = properties.get("eventhub.client.retry.mode");
            if (tempRetryMode.equals("fixed")) {
                //do nothing
            } else if (tempRetryMode.equals("exponential")) {
                retryMode = AmqpRetryMode.EXPONENTIAL;
            } else {
                log.warn("Provided 'eventhub.client.retry.mode' value is not supported. Hence will using hte defalt "
                                 + "value.");
            }
        }
        return new AmqpRetryOptions()
                .setDelay(Duration.ofSeconds(delay))
                .setMaxRetries(maxRetries)
                .setMaxDelay(Duration.ofSeconds(maxDelay))
                .setTryTimeout(Duration.ofSeconds(tryTimeout))
                .setMode(retryMode);

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
    protected CounterMetric createCounter(String name, MetricSchema schema) throws MetricCreationException {
        DefaultCounterMetric counterMetric = new DefaultCounterMetric(name, eventQueue, schema);
        return counterMetric;
    }

    @Override
    protected TimerMetric createTimer(String name) {
        return null;
    }

}
