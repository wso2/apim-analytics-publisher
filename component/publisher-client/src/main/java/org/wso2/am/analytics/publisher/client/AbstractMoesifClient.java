/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.client;

import com.moesif.api.models.EventModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class representing a Moesif client for publishing events and building event responses.
 */
public abstract class AbstractMoesifClient {
    protected final Logger log = LogManager.getLogger(AbstractMoesifClient.class);

    /**
     * Publish method is responsible for publishing events to Moesif.
     */
    public abstract void publish(MetricEventBuilder builder) throws MetricReportingException;
    public abstract void publishBatch(List<MetricEventBuilder> builders);

    public abstract EventModel buildEventResponse(Map<String, Object> data)
            throws IOException, MetricReportingException;

    protected static void populateHeaders(Map<String, Object> data, Map<String, String> reqHeaders,
            Map<String, String> rspHeaders) {
        reqHeaders.put(Constants.MOESIF_USER_AGENT_KEY,
                (String) data.getOrDefault(Constants.USER_AGENT_HEADER, Constants.UNKNOWN_VALUE));
        reqHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, Constants.MOESIF_CONTENT_TYPE_HEADER);

        rspHeaders.put(Constants.VARY_HEADER, "Accept-Encoding");
        rspHeaders.put(Constants.PRAGMA_HEADER, "no-cache");
        rspHeaders.put(Constants.EXPIRES_HEADER, "-1");
        rspHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, "application/json; charset=utf-8");
        rspHeaders.put(Constants.CACHE_CONTROL_HEADER, "no-cache");
    }

    protected List<EventModel> buidEventsfromBuilders(List<MetricEventBuilder> builders) {
        List<EventModel> events = new ArrayList<>();
        for (MetricEventBuilder builder : builders) {
            try {
                Map<String, Object> event = builder.build();
                events.add(this.buildEventResponse(event));
            } catch (MetricReportingException | IOException e) {
                log.error("Builder instance is not duly filled. Event building failed", e);
            }
        }
        return events;
    }
}
