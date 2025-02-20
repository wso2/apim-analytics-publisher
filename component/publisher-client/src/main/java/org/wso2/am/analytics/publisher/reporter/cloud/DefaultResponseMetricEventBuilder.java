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

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.properties.AIMetadata;
import org.wso2.am.analytics.publisher.properties.AITokenUsage;
import org.wso2.am.analytics.publisher.properties.Properties;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.EventMapAttributeFilter;
import org.wso2.am.analytics.publisher.util.UserAgentParser;
import ua_parser.Client;

import java.util.HashMap;
import java.util.Map;

/**
 * Default builder for response metric type. Restrictions are set on the key names that uses can set to the builder.
 * Allows keys and their validity will be checked when populating and availability of all required properties will be
 * checked when building.
 */
public class DefaultResponseMetricEventBuilder extends AbstractMetricEventBuilder {
    private static final Logger log = LogManager.getLogger(DefaultResponseMetricEventBuilder.class);
    protected Map<String, Class> requiredAttributes;
    protected Map<String, Object> eventMap;
    private Boolean isBuilt = false;

    public DefaultResponseMetricEventBuilder() {
        requiredAttributes = DefaultInputValidator.getInstance().getEventProperties(MetricSchema.RESPONSE);
        eventMap = new HashMap<>();
    }

    protected DefaultResponseMetricEventBuilder(Map<String, Class> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
        eventMap = new HashMap<>();
    }

    @Override
    public boolean validate() throws MetricReportingException {
        if (!isBuilt) {
            eventMap.remove(Constants.USER_NAME);
            Map<String, Object> propertyMap = (Map<String, Object>) eventMap.remove(Constants.PROPERTIES);
            if (propertyMap != null) {
                copyDefaultPropertiesToRootLevel(propertyMap);
                extractPropertyObject(propertyMap);
            }
            if (log.isDebugEnabled()) {
                log.debug("Prepared event for publish to Choreo Analytics: " + new Gson().toJson(eventMap));
            }
            for (Map.Entry<String, Class> entry : requiredAttributes.entrySet()) {
                Object attribute = eventMap.get(entry.getKey());
                if (attribute == null) {
                    throw new MetricReportingException(entry.getKey() + " is missing in metric data. This metric event "
                            + "will not be processed further.");
                } else if (!attribute.getClass().equals(entry.getValue())) {
                    throw new MetricReportingException(entry.getKey() + " is expecting a " + entry.getValue() + " type "
                            + "attribute while attribute of type "
                            + attribute.getClass() + " is present.");
                }
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
        eventMap.put(key, value);
        return this;
    }

    @Override
    protected Map<String, Object> buildEvent() {
        if (!isBuilt) {
            // util function to filter required attributes
            eventMap = EventMapAttributeFilter.getInstance().filter(eventMap, requiredAttributes);

            eventMap.put(Constants.EVENT_TYPE, Constants.RESPONSE_EVENT_TYPE);
            // userAgent raw string is not required and removing
            String userAgentHeader = (String) eventMap.remove(Constants.USER_AGENT_HEADER);
            if (userAgentHeader != null) {
                setUserAgentProperties(userAgentHeader);
            }
            isBuilt = true;
        }
        return eventMap;
    }

    private void setUserAgentProperties(String userAgentHeader) {
        String browser = null;
        String platform = null;
        Client client = UserAgentParser.getInstance().parseUserAgent(userAgentHeader);
        if (client != null) {
            browser = client.userAgent.family;
            platform = client.os.family;
        }

        if (browser == null || browser.isEmpty()) {
            browser = Constants.UNKNOWN_VALUE;
        }
        if (platform == null || platform.isEmpty()) {
            platform = Constants.UNKNOWN_VALUE;
        }
        eventMap.put(Constants.USER_AGENT, browser);
        eventMap.put(Constants.PLATFORM, platform);
    }

    private void copyDefaultPropertiesToRootLevel(Map<String, Object> properties) {
        if (properties.get(Constants.API_CONTEXT) != null) {
            String apiContext = (String) properties.remove(Constants.API_CONTEXT);
            eventMap.put(Constants.API_CONTEXT, apiContext);
        }
        properties.remove(Constants.USER_NAME);
    }

}
