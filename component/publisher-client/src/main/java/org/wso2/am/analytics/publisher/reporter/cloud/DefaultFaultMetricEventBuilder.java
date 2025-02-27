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
import org.wso2.am.analytics.publisher.properties.AIMetadata;
import org.wso2.am.analytics.publisher.properties.AITokenUsage;
import org.wso2.am.analytics.publisher.properties.Properties;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder class for fault events.
 */
public class DefaultFaultMetricEventBuilder extends AbstractMetricEventBuilder {
    protected final Map<String, Class> requiredAttributes;
    protected final Map<String, Object> eventMap;

    public DefaultFaultMetricEventBuilder() {
        requiredAttributes = DefaultInputValidator.getInstance().getEventProperties(MetricSchema.ERROR);
        eventMap = new HashMap<>();
    }

    protected DefaultFaultMetricEventBuilder(Map<String, Class> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
        eventMap = new HashMap<>();
    }

    @Override
    public boolean validate() throws MetricReportingException {
        Map<String, Object> propertyMap = (Map<String, Object>) eventMap.remove(Constants.PROPERTIES);
        if (propertyMap != null) {
            extractPropertyObject(propertyMap);
        }
        for (Map.Entry<String, Class> entry : requiredAttributes.entrySet()) {
            Object attribute = eventMap.get(entry.getKey());
            if (attribute == null) {
                throw new MetricReportingException(entry.getKey() + " is missing in metric data. This metric event "
                                                           + "will not be processed further.");
            } else if (!attribute.getClass().equals(entry.getValue())) {
                throw new MetricReportingException(entry.getKey() + " is expecting a " + entry.getValue() + " type "
                                                           + "attribute while attribute of type " + attribute.getClass()
                                                           + " is present");
            }
        }
        return true;
    }

    private void extractPropertyObject(Map<String, Object> properties) {
        Properties propertyObject = new Properties();
        if (properties.get(Constants.AI_METADATA) != null) {
            Map<String, Object> aiMetadata = (Map<String, Object>) properties.remove(Constants.AI_METADATA);
            propertyObject.setAiMetadata(new AIMetadata(aiMetadata));
        }
        if (properties.get(Constants.AI_TOKEN_USAGE) != null) {
            Map<String, Object> aiTokenUsage = (Map<String, Object>) properties.remove(Constants.AI_TOKEN_USAGE);
            propertyObject.setAiTokenUsage(new AITokenUsage(aiTokenUsage));
        }
        if (properties.get(Constants.IS_EGRESS) != null) {
            boolean isEgress = (boolean) properties.remove(Constants.IS_EGRESS);
            propertyObject.setEgress(isEgress);
        }
        if (properties.get(Constants.SUBTYPE) != null) {
            String subType = (String) properties.remove(Constants.SUBTYPE);
            propertyObject.setSubType(subType);
        }
        eventMap.put(Constants.PROPERTIES, propertyObject);
    }

    @Override
    public MetricEventBuilder addAttribute(String key, Object value) throws MetricReportingException {
        //all validation is moved to validate method to reduce analytics data processing latency
        eventMap.put(key, value);
        return this;
    }

    @Override
    protected Map<String, Object> buildEvent() {
        eventMap.put(Constants.EVENT_TYPE, Constants.FAULT_EVENT_TYPE);
        return eventMap;
    }
}
