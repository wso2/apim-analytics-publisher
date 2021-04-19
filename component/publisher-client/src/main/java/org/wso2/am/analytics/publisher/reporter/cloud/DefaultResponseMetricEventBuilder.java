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

import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;
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
    private static final Logger log = Logger.getLogger(DefaultResponseMetricEventBuilder.class);
    private final Map<String, Class> requiredAttributes;
    private Map<String, Object> eventMap;

    protected DefaultResponseMetricEventBuilder() {
        requiredAttributes = DefaultInputValidator.getInstance().getEventProperties(MetricSchema.RESPONSE);
        eventMap = new HashMap<>();
    }

    @Override
    public boolean validate() throws MetricReportingException {
        for (Map.Entry<String, Class> entry : requiredAttributes.entrySet()) {
            Object attribute = eventMap.get(entry.getKey());
            if (attribute == null) {
                throw new MetricReportingException(entry.getKey() + " is missing in metric data. This metric event "
                                                           + "will not be processed further.");
            } else if (!attribute.getClass().equals(entry.getValue())) {
                throw new MetricReportingException(entry.getKey() + " is expecting a " + entry.getValue() + " type "
                                                           + "attribute while attribute of type " + attribute.getClass()
                                                           + " is present.");
            }
        }
        return true;
    }

    @Override public MetricEventBuilder addAttribute(String key, Object value) throws MetricReportingException {
        eventMap.put(key, value);
        return this;
    }

    @Override
    protected Map<String, Object> buildEvent() {
        eventMap.put(Constants.EVENT_TYPE, Constants.RESPONSE_EVENT_TYPE);
        // userAgent raw string is not required and removing
        String userAgentHeader = (String) eventMap.get(Constants.USER_AGENT_HEADER);
        // userAgentHeader will not null since it is already validated
        //if it is null then event has been already built. In such a case will return Map as it is
        if (userAgentHeader == null) {
            return eventMap;
        }
        setUserAgentProperties(userAgentHeader);
        eventMap.remove(Constants.USER_AGENT_HEADER);
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

}
