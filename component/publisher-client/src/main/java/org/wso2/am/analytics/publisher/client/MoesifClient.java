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
package org.wso2.am.analytics.publisher.client;

import com.moesif.api.MoesifAPIClient;
import com.moesif.api.controllers.APIController;
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import com.moesif.api.http.response.HttpResponse;
import com.moesif.api.models.EventModel;
import com.moesif.api.models.EventRequestBuilder;
import com.moesif.api.models.EventRequestModel;
import com.moesif.api.models.EventResponseBuilder;
import com.moesif.api.models.EventResponseModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.properties.MoesifEventData;
import org.wso2.am.analytics.publisher.properties.OrgMoesifKeyMapping;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.HttpStatusHelper;
import org.wso2.am.analytics.publisher.util.LogSanitizer;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This client is responsible for publishing events from choreo backend
 * to Moesif Analytics dahsboard
 */
public class MoesifClient extends AbstractMoesifClient {
    private final Logger log = LogManager.getLogger(MoesifClient.class);
    private final MoesifKeyRetriever keyRetriever;

    public MoesifClient(MoesifKeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
    }

    /**
     * Gets the OrgMoesifKeyMapping for a specific organization.
     *
     * @param orgId The organization FID.
     * @return OrgMoesifKeyMapping instance, or null if not found.
     */
    private OrgMoesifKeyMapping getOrgMoesifKeyMapping(String orgId) {
        Map<String, Map<String, String>> orgIDMoesifKeyMap = keyRetriever.getMoesifKeyMap();
        if (orgIDMoesifKeyMap.containsKey(orgId)) {
            return new OrgMoesifKeyMapping(orgId, orgIDMoesifKeyMap.get(orgId));
        }
        return null;
    }

    /**
     * publish method is responsible for checking the availability of relevant
     * moesif key
     * and initiating moesif client sdk.
     */
    @Override
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();
        
        String orgId = (String) event.get(Constants.ORGANIZATION_ID);
        if (orgId == null || orgId.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Event missing organization ID. Skipping event.");
            }
            return;
        }
        
        Map properties = (LinkedHashMap) event.get(Constants.PROPERTIES);
        if (properties == null) {
            log.debug("Event missing properties. Skipping event for organization: {}", orgId);
            return;
        }
        
        String eventEnvironment = (String) properties.get(Constants.DEPLOYMENT_TYPE);
        if (eventEnvironment == null || eventEnvironment.isEmpty()) {
            log.debug("Event missing environment for organization: {}. Skipping event.", orgId);
            return;
        }
        
        OrgMoesifKeyMapping orgKeyMapping = getOrgMoesifKeyMapping(orgId);
        if (orgKeyMapping == null) {
            log.debug("No Moesif key found for organization: {}. Skipping event.", orgId);
            return;
        }

        String moesifKey;

        // If old records with only one environment, use that single key
        if (orgKeyMapping.hasSingleEnvironment()) {
            moesifKey = orgKeyMapping.getSingleEnvironmentKey();
        } else {
            // Multiple environments exist, get key for specific environment
            moesifKey = orgKeyMapping.getMoesifKeyForEnvironment(eventEnvironment);
            if (moesifKey == null) {
                log.debug("No Moesif key found for organization: {} and environment: {}. Skipping event.",
                        orgId, eventEnvironment);
                return;
            }
        }

        // init moesif api client
        MoesifAPIClient client = new MoesifAPIClient(moesifKey);
        APIController api = client.getAPI();

        APICallBack<HttpResponse> callBack = createMoesifCallBack(() -> doRetry(orgId, builder),
                "Single event", orgId);
        try {
            api.createEventAsync(buildEventResponse(event), callBack);
        } catch (IOException e) {
            log.error("Analytics event sending failed. Event will be dropped", e);
        }
    }

    @Override
    public void publishBatch(List<MetricEventBuilder> builders) {
        if (builders == null || builders.isEmpty()) {
            log.debug("No events to publish in batch");
            return;
        }

        Map<String, OrgMoesifKeyMapping> orgMappings = groupEventsByOrganizationAndEnvironment(builders);
        
        log.info("Publishing batch of events for {} organizations", orgMappings.size());

        for (Map.Entry<String, OrgMoesifKeyMapping> entry : orgMappings.entrySet()) {
            String orgId = entry.getKey();
            OrgMoesifKeyMapping orgMapping = entry.getValue();

            try {
                publishBatchForOrganization(orgMapping);
            } catch (Exception e) {
                log.error("Error while processing events for organization: {}", orgId, e);
            }
        }
    }

    @Override
    public EventModel buildEventResponse(Map<String, Object> data) throws IOException, MetricReportingException {
        Map<String, String> reqHeaders = new HashMap<>();
        Map<String, String> rspHeaders = new HashMap<>();

        populateHeaders(data, reqHeaders, rspHeaders);

        EventRequestModel eventReq;
        EventResponseModel eventRsp;
        EventModel eventModel = new EventModel();
        String modifiedUserName;
        if (!data.containsKey(Constants.ERROR_CODE)) {
            final String userIP = (String) data.get(Constants.USER_IP);
            final String userName = (String) data.get(Constants.USER_NAME);
            final String apiContext = (String) data.get(Constants.API_CONTEXT);
            final String apiResourceTemplate = (String) data.get(Constants.API_RESOURCE_TEMPLATE);
            final long responseLatency = (long) data.get(Constants.RESPONSE_LATENCY);

            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
            Instant requestTimestamp = Instant.
                    from(dateTimeFormatter.parse((String) data.get(Constants.REQUEST_TIMESTAMP)));
            Instant responseTimestamp = requestTimestamp.plusMillis(responseLatency);

            LinkedHashMap properties = (LinkedHashMap) data.get(Constants.PROPERTIES);
            String gwURL = (String) properties.get(Constants.GATEWAY_URL);
            String uri = apiContext + apiResourceTemplate;
            if (gwURL != null) {
                uri = gwURL;
            }

            eventReq = new EventRequestBuilder()
                    .time(Date.from(requestTimestamp))
                    .uri(uri)
                    .verb((String) data.get(Constants.API_METHOD))
                    .apiVersion((String) data.get(Constants.API_VERSION))
                    .ipAddress(userIP)
                    .headers(reqHeaders)
                    .build();

            eventRsp = new EventResponseBuilder()
                    .time(Date.from(responseTimestamp))
                    .status((int) data.get(Constants.TARGET_RESPONSE_CODE))
                    .headers(rspHeaders)
                    .build();

            if (userName.contains("@carbon.super")) {
                modifiedUserName = userName.replace("@carbon.super", "");
            } else {
                modifiedUserName = userName;
            }

        } else {
            LinkedHashMap properties = (LinkedHashMap) data.get(Constants.PROPERTIES);

            modifiedUserName = (String) data.get(Constants.API_CREATION);

            String apiContext = (String) data.get(Constants.API_CONTEXT);
            String gwURL = (String) properties.get(Constants.GATEWAY_URL);
            String apiResourceTemplate = (String) data.get(Constants.API_RESOURCE_TEMPLATE);
            String uri = apiContext + apiResourceTemplate;

            if (gwURL != null) {
                uri = gwURL;
            }

            Date errorRequestTimestamp = new Date();

            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                errorRequestTimestamp = dateFormat.parse((String) data.get(Constants.REQUEST_TIMESTAMP));
            } catch (ParseException e) {
                log.error("Error parsing error request timestamp", e);
            }

            eventReq = new EventRequestBuilder()
                    .time(errorRequestTimestamp)
                    .uri(uri)
                    .verb((String) properties.get(Constants.API_METHOD))
                    .apiVersion((String) data.get(Constants.API_VERSION))
                    .headers(reqHeaders)
                    .build();

            eventRsp = new EventResponseBuilder()
                    .time(new Date())
                    .status((int) data.get(Constants.PROXY_RESPONSE_CODE))
                    .headers(rspHeaders)
                    .build();
        }

        eventModel.setRequest(eventReq);
        eventModel.setResponse(eventRsp);
        eventModel.setUserId(modifiedUserName);
        eventModel.setCompanyId(null);

        return eventModel;
    }

    private APICallBack<HttpResponse> createMoesifCallBack(
            Runnable retryAction, String eventType, String orgId) {
        return new APICallBack<HttpResponse>() {
            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                if (HttpStatusHelper.isSuccess(statusCode)) {
                    log.debug("{} successfully published.", eventType);
                } else if (HttpStatusHelper.shouldRetry(statusCode)) {
                    log.error("{} publishing failed for organization: {}. Moesif returned {}. Response {}",
                            eventType,
                            LogSanitizer.sanitize(orgId),
                            LogSanitizer.sanitize(String.valueOf(statusCode)),
                            response.getRawBody());
                    retryAction.run();
                } else {
                    log.error("{} Event publishing failed for organization: {}. Response {}.",
                            eventType,
                            LogSanitizer.sanitize(orgId),
                            response.getRawBody());
                }
            }

            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();

                if (HttpStatusHelper.shouldRetry(statusCode)) {
                    log.error("{} publishing failed for organization: {}. Moesif returned {}. Retrying",
                            eventType,
                            orgId.replaceAll("[\r\n]", ""),
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                    retryAction.run();
                } else if (HttpStatusHelper.isClientError(statusCode)) {
                    log.error("{} publishing failed for organization: {} due to error: {}",
                            eventType,
                            orgId.replaceAll("[\r\n]", ""),
                            error.getMessage().replaceAll("[\r\n]", ""));
                } else {
                    log.error("{} publishing failed for organization: {}. Retrying.",
                            eventType,
                            orgId.replaceAll("[\r\n]", ""));
                    retryAction.run();
                }
            }
        };
    }

    private void doRetry(OrgMoesifKeyMapping orgMapping) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();

        if (currentAttempt > 0) {
            currentAttempt -= 1;
            MoesifClientContextHolder.PUBLISH_ATTEMPTS.set(currentAttempt);
            try {
                Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH);
                publishBatchForOrganization(orgMapping);
            } catch (InterruptedException e) {
                log.error("Failing retry attempt at Moesif client", e);
            }
        } else if (currentAttempt == 0) {
            log.error("Failed all retrying attempts. Event will be dropped for organization {}",
                    orgMapping.getOrganizationId().replaceAll("[\r\n]", ""));
        }
    }

    private void doRetry(String orgId, MetricEventBuilder builder) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();

        if (currentAttempt > 0) {
            currentAttempt -= 1;
            MoesifClientContextHolder.PUBLISH_ATTEMPTS.set(currentAttempt);
            try {
                Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH);
                publish(builder);
            } catch (MetricReportingException e) {
                log.error("Failing retry attempt at Moesif client", e);
            } catch (InterruptedException e) {
                log.error("Failing retry attempt at Moesif client", e);
            }
        } else if (currentAttempt == 0) {
            log.error("Failed all retrying attempts. Event will be dropped for organization {}",
                    orgId.replaceAll("[\r\n]", ""));
        }
    }

    /**
     * Publishes a batch of events for a specific organization using true batch API.
     * Events are already grouped by environment, so each environment batch is published separately.
     */
    private void publishBatchForOrganization(OrgMoesifKeyMapping orgMapping) {
        String orgId = orgMapping.getOrganizationId();
        
        if (!orgMapping.hasKeys()) {
            log.warn("No Moesif key found for organization: {}. Skipping events", orgId);
            return;
        }

        for (String environment : orgMapping.getEnvironments()) {
            List<MoesifEventData> events = orgMapping.getEventsForEnvironment(environment);
            
            String moesifKey;
            if (orgMapping.hasSingleEnvironment()) {
                moesifKey = orgMapping.getSingleEnvironmentKey();
            } else {
                moesifKey = orgMapping.getMoesifKeyForEnvironment(environment);
                if (moesifKey == null) {
                    log.warn("No Moesif key found for organization: {} and environment: {}. Skipping {} events", 
                        orgId, environment, events.size());
                    continue;
                }
            }
            
            List<EventModel> validEvents = new ArrayList<>();
            for (MoesifEventData eventData : events) {
                try {
                    validEvents.add(buildEventResponse(eventData.getEventData()));
                } catch (Exception e) {
                    log.error("Failed to build event for batch processing", e);
                }
            }

            if (validEvents.isEmpty()) {
                log.debug("No valid events to publish for organization: {} and environment: {}", orgId, environment);
                continue;
            }

            MoesifAPIClient client = new MoesifAPIClient(moesifKey);
            APIController api = client.getAPI();

            OrgMoesifKeyMapping retryMapping = new OrgMoesifKeyMapping(orgId, orgMapping.getEnvironmentKeyMap());
            Map<String, List<MoesifEventData>> retryBatch = new HashMap<>();
            retryBatch.put(environment, events);
            retryMapping.setEnvironmentEventBatches(retryBatch);
            
            APICallBack<HttpResponse> callBack = createMoesifCallBack(
                () -> doRetry(retryMapping),
                "Batch event", orgId);

            try {
                if (validEvents.size() == 1) {
                    log.info("Publishing single event for org: {} environment: {}", orgId, environment);
                    api.createEventAsync(validEvents.get(0), callBack);
                } else {
                    log.info("Publishing batch of {} events for org: {} environment: {}", 
                    validEvents.size(), orgId, environment);
                    api.createEventsBatchAsync(validEvents, callBack);
                }
            } catch (IOException e) {
                log.error("Analytics event sending failed for organization {} and environment {}", 
                orgId, environment, e);
            }
        }
    }
    /**
     * Groups events by organization ID, and within each organization, groups them by environment.
     */
    private Map<String, OrgMoesifKeyMapping> groupEventsByOrganizationAndEnvironment(
            List<MetricEventBuilder> builders) {
        Map<String, OrgMoesifKeyMapping> orgMappings = new HashMap<>();
        
        for (MetricEventBuilder builder : builders) {
            try {
                Map<String, Object> event = builder.build();
                String orgId = (String) event.get(Constants.ORGANIZATION_ID);
                if (orgId == null || orgId.isEmpty()) {
                    log.warn("Skipping event with no organization ID");
                    continue;
                }

                Map properties = (LinkedHashMap) event.get(Constants.PROPERTIES);
                String environment = (String) properties.get(Constants.DEPLOYMENT_TYPE);
                if (environment == null || environment.isEmpty()) {
                    log.warn("Skipping event with no environment for organization: {}", orgId);
                    continue;
                }

                OrgMoesifKeyMapping orgMapping = orgMappings.computeIfAbsent(orgId, id -> 
                    getOrgMoesifKeyMapping(id)
                );
                
                if (orgMapping == null || !orgMapping.hasKeys()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping event for organization {} - no Moesif keys configured", orgId);
                    }
                    continue;
                }
                
                orgMapping.addEvent(environment, event);
            } catch (Exception e) {
                log.error("Failed to extract organization ID or environment from event, skipping", e);
            }
        }
        return orgMappings;
    }
}
