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

package org.wso2.am.analytics.publisher.reporter.elk;

import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.UserAgentParser;
import ua_parser.Client;

import java.util.HashMap;
import java.util.Map;

/**
 * Event builder for log Metric Reporter
 */
public class ELKMetricEventBuilder extends AbstractMetricEventBuilder {
    private Map<String, Object> eventMap = new HashMap<>();
    private Boolean isBuilt = false;

    @Override
    protected Map<String, Object> buildEvent() {
        if (!isBuilt) {
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

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public MetricEventBuilder addAttribute(String key, Object value) throws MetricReportingException {
        eventMap.put(key, value);
        return this;
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
