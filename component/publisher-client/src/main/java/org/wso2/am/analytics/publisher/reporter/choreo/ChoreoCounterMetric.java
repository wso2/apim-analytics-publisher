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

package org.wso2.am.analytics.publisher.reporter.choreo;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.client.EventHubClient;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;

import java.util.Map;

/**
 * Implementation of {@link CounterMetric} for Choroe Metric Reporter
 */
public class ChoreoCounterMetric implements CounterMetric {
    private static final Logger log = Logger.getLogger(ChoreoCounterMetric.class);
    private String name;
    private EventHubClient client;
    private String[] requiredAttributes;

    protected ChoreoCounterMetric(String name, EventHubClient client, MetricSchema schema) {
        this.name = name;
        this.client = client;
        requiredAttributes = ChoreoInputValidator.getInstance().getEventSchema(schema);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int incrementCount(Map<String, String> attributes) throws MetricReportingException {
        if (attributes != null) {
            validateAttributes(attributes);
            String event = new Gson().toJson(attributes);
            client.sendEvent(event);
            return 0;
        } else {
            throw new MetricReportingException("Event attributes cannot be null");
        }
    }

    private void validateAttributes(Map<String, String> attributes) throws MetricReportingException {
        for (String attributeKey : requiredAttributes) {
            String attribute = attributes.get(attributeKey);
            if (attribute == null || attribute.isEmpty()) {
                throw new MetricReportingException(attributeKey + " is missing in metric data");
            }
        }
    }
}
