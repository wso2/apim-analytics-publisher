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

import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default builder for response metric type. Restrictions are set on the key names that uses can set to the builder.
 * Allows keys and their validity will be checked when populating and availability of all required properties will be
 * checked when building.
 */
public class DefaultResponseMetricEventBuilder extends AbstractMetricEventBuilder {
    private final List<String> requiredAttributes;
    private Map<String, Object> eventMap;

    protected DefaultResponseMetricEventBuilder() {
        requiredAttributes = DefaultInputValidator.getInstance().getEventProperties(MetricSchema.RESPONSE);
        eventMap = new HashMap<>();
    }

    @Override
    public boolean validate() throws MetricReportingException {
        for (String attributeKey : requiredAttributes) {
            Object attribute = eventMap.get(attributeKey);
            if (attribute == null) {
                throw new MetricReportingException(attributeKey + " is missing in metric data");
            } else if (attribute instanceof String) {
                if (((String) attribute).isEmpty()) {
                    throw new MetricReportingException(attributeKey + " is missing in metric data");
                }
            }
        }
        return true;
    }

    @Override
    protected boolean isKeyPresent(String key) {
        return requiredAttributes.contains(key);
    }

    @Override
    protected Map<String, Object> buildEvent() {
        return eventMap;
    }

    @Override protected MetricEventBuilder addVerifiedAttribute(String key, Object value) {
        eventMap.put(key, value);
        return this;
    }
}
