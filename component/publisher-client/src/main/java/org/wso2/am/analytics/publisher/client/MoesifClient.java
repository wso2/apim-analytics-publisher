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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.properties.OrgMoesifKeyMapping;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;
import org.wso2.am.analytics.publisher.util.Constants;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moesif Client is responsible for sending events to
 * Moesif Analytics Dashboard.
 */
public class MoesifClient {
    private final Logger log = LoggerFactory.getLogger(MoesifClient.class);
    private final MoesifKeyRetriever keyRetriever;

    public MoesifClient(MoesifKeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
    }

    private void doRetry(String orgId, MetricEventBuilder builder) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();
        log.debug("Retry attempt for organization: {}. Remaining attempts: {}", orgId, currentAttempt);

        if (currentAttempt > 0) {
            currentAttempt -= 1;
            MoesifClientContextHolder.PUBLISH_ATTEMPTS.set(currentAttempt);
            try {
                log.debug("Waiting {}ms before retry for organization: {}", 
                        MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH, orgId);
                Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH);
                log.debug("Retrying publish for organization: {}", orgId);
                publish(builder);
            } catch (MetricReportingException e) {
                log.error("Retry attempt failed for organization: {}", orgId.replaceAll("[\r\n]", ""), e);
            } catch (InterruptedException e) {
                log.error("Retry interrupted for organization: {}", orgId.replaceAll("[\r\n]", ""), e);
                Thread.currentThread().interrupt();
            }
        } else if (currentAttempt == 0) {
            log.error("All retry attempts exhausted. Event will be dropped for organization: {}",
                    orgId.replaceAll("[\r\n]", ""));
        }
    }

    /**
          * Gets the OrgMoesifKeyMapping for a specific organization.
     *
     * @param orgId The organization FID.
     * @return OrgMoesifKeyMapping instance, or null if not found.
     */
    private OrgMoesifKeyMapping getOrgMoesifKeyMapping(String orgId) {
        Map<String, Map<String, String>> orgIDMoesifKeyMap = keyRetriever.getMoesifKeyMap();
        log.debug("Looking up Moesif key for organization: {}. Available organizations: {}", 
                orgId, orgIDMoesifKeyMap.keySet());
        if (orgIDMoesifKeyMap.containsKey(orgId)) {
            Map<String, String> envKeys = orgIDMoesifKeyMap.get(orgId);
            log.debug("Found Moesif keys for organization: {} with environments: {}", orgId, envKeys.keySet());
            return new OrgMoesifKeyMapping(orgId, envKeys);
        }
        log.warn("No Moesif key mapping found for organization: {}", orgId);
        return null;
    }

    /**
     * publish method is responsible for checking the availability of relevant
     * moesif key
     * and initiating moesif client sdk.
     */
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();
        log.info("Publishing metric event for Moesif analytics.");
        log.debug("Event data structure: {}", event.keySet());
        String orgId = (String) event.get(Constants.ORGANIZATION_ID);

        if (orgId == null || orgId.isEmpty()) {
            log.warn("Event missing organization ID. Skipping event.");
            return;
        }
        log.debug("Processing event for organization: {}", orgId);

        Map properties = (LinkedHashMap) event.get(Constants.PROPERTIES);
        
        if (properties == null) {
            log.warn("Event missing properties. Skipping event for organization: {}", orgId);
            return;
        }
        
        String eventEnvironment = (String) properties.get(Constants.DEPLOYMENT_TYPE);
        if (eventEnvironment == null || eventEnvironment.isEmpty()) {
            log.warn("Event missing environment (deployment type) for organization: {}. Skipping event.", orgId);
            return;
        }
        log.debug("Event environment: {}", eventEnvironment);
        
        OrgMoesifKeyMapping orgKeyMapping = getOrgMoesifKeyMapping(orgId);
        if (orgKeyMapping == null) {
            log.warn("No Moesif key mapping found for organization: {}. Skipping event.", orgId);
            return;
        }

        String moesifKey;

        // If old records with only one environment, use that single key
        if (orgKeyMapping.hasSingleEnvironment()) {
            moesifKey = orgKeyMapping.getSingleEnvironmentKey();
            log.debug("Using single environment Moesif key for organization: {}", orgId);
        } else {
            // Multiple environments exist, get key for specific environment
            log.debug("Multiple environments detected. Looking up key for environment: {}", eventEnvironment);
            moesifKey = orgKeyMapping.getMoesifKeyForEnvironment(eventEnvironment);
            if (moesifKey == null) {
                log.warn("No Moesif key found for organization: {} and environment: {}. Skipping event.",
                        orgId, eventEnvironment);
                return;
            }
        }
        
        // Validate the key before using it
        if (moesifKey == null || moesifKey.isEmpty()) {
            log.error("Invalid Moesif key (null or empty) for organization: {} and environment: {}. Skipping event.",
                    orgId, eventEnvironment);
            return;
        }
        
        log.debug("Using Moesif key for organization: {} and environment: {} (key length: {})", 
                orgId, eventEnvironment, moesifKey.length());

        try {
            log.debug("Initializing Moesif API client for organization: {}", orgId);
            MoesifAPIClient client = new MoesifAPIClient(moesifKey);

        // api object is a singleton which will make calls to
        // moesif endpoints with the latest MoesifAPI client being provided.
        // Hence avoid maintaining a map of MoesifAPIClient against moesif keys.
        APIController api = client.getAPI();

        APICallBack<HttpResponse> callBack = new APICallBack<HttpResponse>() {
            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                log.debug("Moesif API response received for organization: {}. Status code: {}", orgId, statusCode);
                if (statusCode == 200 || statusCode == 201 || statusCode == 202 || statusCode == 204) {
                    log.info("Event successfully published to Moesif for organization: {}", orgId);
                } else if (statusCode >= 400 && statusCode < 500) {
                    log.error("Client error publishing event for organization: {}. Moesif returned {}. Event will be dropped.",
                            orgId.replaceAll("[\r\n]", ""), String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else {
                    log.error("Event publishing failed for organization: {}. Retrying.",
                            orgId.replaceAll("[\r\n]", ""));
                    doRetry(orgId, builder);
                }
            }

            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();
                log.debug("Moesif API failure callback triggered for organization: {}. Status code: {}", 
                        orgId, statusCode);

                if (statusCode >= 400 && statusCode < 500) {
                    log.error("Client error in onFailure for organization: {}. Moesif returned {}. Event will be dropped.",
                            orgId.replaceAll("[\r\n]", ""), String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else if (error != null) {
                    log.error("Event publishing failed for organization: {}. Error: {}",
                            orgId.replaceAll("[\r\n]", ""),
                            error.getMessage().replaceAll("[\r\n]", ""), error);
                } else {
                    log.error("Event publishing failed for organization: {}. Retrying.",
                            orgId.replaceAll("[\r\n]", ""));
                    doRetry(orgId, builder);
                }
            }
        };
        try {
            log.debug("Building event model for organization: {}", orgId);
            EventModel eventModel = buildEventResponse(event);
            log.debug("Sending event asynchronously to Moesif for organization: {}", orgId);
            api.createEventAsync(eventModel, callBack);
        } catch (IOException e) {
            log.error("Analytics event sending failed for organization: {}. Event will be dropped", 
                    orgId.replaceAll("[\r\n]", ""), e);
        } catch (MetricReportingException e) {
            log.error("Failed to build event model for organization: {}. Event will be dropped", 
                    orgId.replaceAll("[\r\n]", ""), e);
        } catch (Exception e) {
            log.error("Unexpected error during Moesif client initialization for organization: {}. " +
                    "This may indicate an invalid Moesif key. Event will be dropped", 
                    orgId.replaceAll("[\r\n]", ""), e);
        }
    }

    private EventModel buildEventResponse(Map<String, Object> data) throws IOException, MetricReportingException {
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

    private static void populateHeaders(Map<String, Object> data, Map<String, String> reqHeaders,
            Map<String, String> rspHeaders) {
        reqHeaders.put(Constants.MOESIF_USER_AGENT_KEY,
                (String) data.getOrDefault(Constants.USER_AGENT_HEADER, Constants.UNKNOWN_VALUE));
        reqHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, Constants.MOESIF_CONTENT_TYPE_HEADER);

        rspHeaders.put("Vary", "Accept-Encoding");
        rspHeaders.put("Pragma", "no-cache");
        rspHeaders.put("Expires", "-1");
        rspHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, "application/json; charset=utf-8");
        rspHeaders.put("Cache-Control", "no-cache");
    }
}
