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
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.HttpStatusHelper;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple Moesif client implementation for publishing events from APIM
 * to Moesif analytics dashboard.
 */
public class SimpleMoesifClient extends AbstractMoesifClient {
    private final MoesifAPIClient moesifAPIClient;
    private final APIController api;

    public SimpleMoesifClient(String key) {
        this.moesifAPIClient = new MoesifAPIClient(key);
        this.api = moesifAPIClient.getAPI();
    }

    public SimpleMoesifClient(String key, String url) {
        this.moesifAPIClient = new MoesifAPIClient(key, url);
        this.api = moesifAPIClient.getAPI();
    }

    @Override
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();

        APICallBack<HttpResponse> callBack = createMoesifCallback(() -> doRetry(builder),
                "Single event");
        try {
            api.createEventAsync(buildEventResponse(event), callBack);
        } catch (IOException e) {
            log.error("Analytics event sending failed. Event will be dropped", e);
        }
    }

    @Override
    public void publishBatch(List<MetricEventBuilder> builders) {
        if (builders == null || builders.isEmpty()) {
            return;
        }
        List<EventModel> events = buidEventsfromBuilders(builders);

        APICallBack<HttpResponse> callBack = createMoesifCallback(() -> doRetry(builders),
                "Batch of " + builders.size() + " events");
        try {
            api.createEventsBatchAsync(events, callBack);
        } catch (IOException e) {
            log.error("Analytics event sending failed. Event will be dropped", e);
        }
    }

    @Override
    public EventModel buildEventResponse(Map<String, Object> data) throws IOException {
        Map<String, String> reqHeaders = new HashMap<>();
        Map<String, String> rspHeaders = new HashMap<>();

        populateHeaders(data, reqHeaders, rspHeaders);

        EventRequestModel eventReq;
        EventResponseModel eventRsp;
        EventModel eventModel = new EventModel();
        String modifiedUserName;

        Map<String, Object> metadata = new HashMap<>();
        populateMetadata(data, metadata);

        if (!data.containsKey(Constants.ERROR_CODE)) {
            final String userIP = (String) data.get(Constants.USER_IP);
            final String userName = (String) data.get(Constants.USER_NAME);
            final String apiContext = (String) ((LinkedHashMap) data.get(Constants.PROPERTIES)).get(
                    Constants.API_CONTEXT);
            final String apiResourceTemplate = (String) data.get(Constants.API_RESOURCE_TEMPLATE);
            final long responseLatency = (long) data.get(Constants.RESPONSE_LATENCY);

            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
            Instant requestTimestamp = Instant.from(
                    dateTimeFormatter.parse((String) data.get(Constants.REQUEST_TIMESTAMP)));
            Instant responseTimestamp = requestTimestamp.plusMillis(responseLatency);

            LinkedHashMap properties = (LinkedHashMap) data.get(Constants.PROPERTIES);
            String gwURL = (String) properties.get(Constants.GATEWAY_URL);
            String uri = apiContext + apiResourceTemplate;
            if (gwURL != null) {
                uri = gwURL;
            }

            eventReq = new EventRequestBuilder().time(Date.from(requestTimestamp)).uri(uri)
                    .verb((String) data.get(Constants.API_METHOD)).apiVersion((String) data.get(Constants.API_VERSION))
                    .ipAddress(userIP).headers(reqHeaders).build();

            eventRsp = new EventResponseBuilder().time(Date.from(responseTimestamp))
                    .status((int) data.get(Constants.PROXY_RESPONSE_CODE)).headers(rspHeaders).build();

            if (userName.contains("@carbon.super")) {
                modifiedUserName = userName.replace("@carbon.super", "");
            } else {
                modifiedUserName = userName;
            }

        } else {

            modifiedUserName = (String) data.get(Constants.API_CREATION);

            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
            Instant requestTimestamp = Instant.from(
                    dateTimeFormatter.parse((String) data.get(Constants.REQUEST_TIMESTAMP)));

            eventReq = new EventRequestBuilder().time(Date.from(requestTimestamp)).uri(Constants.NOT_APPLICABLE)
                    .verb(Constants.NOT_APPLICABLE).apiVersion((String) data.get(Constants.API_VERSION))
                    .headers(reqHeaders).build();

            Date dateNow = Date.from(Instant.now());

            eventRsp = new EventResponseBuilder().time(dateNow).status((int) data.get(Constants.PROXY_RESPONSE_CODE))
                    .headers(rspHeaders).build();
        }

        eventModel.setRequest(eventReq);
        eventModel.setResponse(eventRsp);
        eventModel.setUserId(modifiedUserName);
        eventModel.setMetadata(metadata);
        eventModel.setCompanyId(null);

        return eventModel;
    }
    /**
     * Populates the metadata map with required analytics fields from the source data.
     *
     * This method filters and transfers specific analytics-related fields from the source
     * data map to the metadata map, ensuring only required fields with non-null values
     * are included. All values are converted to String format for consistent metadata handling.
     *@param data     The source data map containing various analytics fields and values.
     *@param metadata The target metadata map to be populated with filtered analytics data.
     **/
    private void populateMetadata(Map<String, Object> data, Map<String, Object> metadata) {
        Set<String> requiredKeys = new HashSet<>(Arrays.asList(
                Constants.API_ID, Constants.API_METHOD, Constants.API_NAME,
                Constants.API_TYPE, Constants.APPLICATION_ID, Constants.APPLICATION_NAME, Constants.APPLICATION_OWNER,
                Constants.BACKEND_LATENCY, Constants.GATEWAY_TYPE, Constants.KEY_TYPE, Constants.EVENT_TYPE,
                Constants.API_CREATION, Constants.API_CREATOR_TENANT_DOMAIN, Constants.API_VERSION,
                Constants.CORRELATION_ID, Constants.RESPONSE_CACHE_HIT, Constants.USER_NAME,
                Constants.RESPONSE_MEDIATION_LATENCY, Constants.DESTINATION, Constants.ERROR_CODE,
                Constants.ERROR_MESSAGE, Constants.ERROR_TYPE, Constants.TARGET_RESPONSE_CODE,
                Constants.REQUEST_MEDIATION_LATENCY
        ));

        data.entrySet().stream().filter(entry -> requiredKeys.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> metadata.put(entry.getKey(), String.valueOf(entry.getValue())));

        // Add AI metadata and token usage if present
        populateAIInfo(data, metadata);

    }

    /**
     * Retries publishing the Batch of events using the provided List of `MetricEventBuilder`
     * if retry attempts are available.
     * Decrements the retry attempt count and waits for a specified duration before retrying.
     * Logs errors if the retry attempt fails or if all retry attempts are exhausted.
     *
     * @param builders The List of `MetricEventBuilder` containing the event data to be published.
     */
    private void doRetry(List<MetricEventBuilder> builders) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();

        if (currentAttempt > 0) {
            currentAttempt -= 1;
            MoesifClientContextHolder.PUBLISH_ATTEMPTS.set(currentAttempt);
            try {
                Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH);
                publishBatch(builders);
            } catch (InterruptedException e) {
                log.error("Failing retry attempt at Moesif client", e);
            }
        } else if (currentAttempt == 0) {
            log.error("Failed all retrying attempts. Event will be dropped");
        }
    }
    /**
     * Retries publishing the event using the provided `MetricEventBuilder` if retry attempts are available.
     * Decrements the retry attempt count and waits for a specified duration before retrying.
     * Logs errors if the retry attempt fails or if all retry attempts are exhausted.
     *
     * @param builder The List of `MetricEventBuilder` containing the event data to be published.
     */
    private void doRetry(MetricEventBuilder builder) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();

        if (currentAttempt > 0) {
            currentAttempt -= 1;
            MoesifClientContextHolder.PUBLISH_ATTEMPTS.set(currentAttempt);
            try {
                Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT_PUBLISH);
                publish(builder);
            } catch (InterruptedException e) {
                log.error("Failing retry attempt at Moesif client", e);
            } catch (MetricReportingException e) {
                log.error("Failed to publish event during retry attempt", e);
            }
        } else if (currentAttempt == 0) {
            log.error("Failed all retrying attempts. Event will be dropped");
        }
    }
    private APICallBack<HttpResponse> createMoesifCallback(Runnable retryAction, String operationType) {
        return new APICallBack<HttpResponse>() {
            /**
             * Handles successful HTTP response from Moesif API.
             */
            @Override
            public void onSuccess(HttpContext httpContext, HttpResponse response) {
                int statusCode = httpContext.getResponse().getStatusCode();
                if (HttpStatusHelper.isSuccess(statusCode)) {
                    log.debug("{} successfully published. Status: {}", operationType, statusCode);
                } else if (HttpStatusHelper.shouldRetry(statusCode)) {
                    log.error("{} publishing failed. Moesif returned {}. Response: {}. Retrying...",
                            operationType,
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""),
                            response.getRawBody());
                    retryAction.run();
                } else {
                    log.error("{} publishing failed. Moesif returned {}. Response: {}. No retry.",
                            operationType,
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""),
                            response.getRawBody());
                }
            }
            /**
             * Handles failed HTTP responses from Moesif API.
             * Processes failures based on status code and error details.
             */
            @Override
            public void onFailure(HttpContext httpContext, Throwable error) {
                int statusCode = httpContext.getResponse().getStatusCode();
                String errorMessage = error != null ? error.getMessage() : "Unknown error";

                if (HttpStatusHelper.shouldRetry(statusCode)) {
                    log.error("{} publishing failed. Moesif returned {}. Retrying...",
                            operationType,
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                    retryAction.run();
                } else if (HttpStatusHelper.isClientError(statusCode)) {
                    log.error("{} publishing failed. Moesif returned {} due to error: {}",
                            operationType,
                            statusCode,
                            errorMessage.replaceAll("[\r\n]", ""));
                } else {
                    log.error("{} publishing failed due to error: {}",
                            operationType,
                            errorMessage.replaceAll("[\r\n]", ""));
                }
            }
        };
    }

    /**
     * Populates AI-related metadata fields in the provided metadata map if present in the source data.
     *
     * This method checks for AI metadata and token usage within the properties of the source data map,
     * and adds them to the metadata map if available.
     *
     * @param data     The source data map containing analytics fields and properties.
     * @param metadata The target metadata map to be populated with AI-related information.
     */
    private void populateAIInfo(Map<String, Object> data, Map<String, Object> metadata) {
        if (data.get(Constants.PROPERTIES) != null) {
            Map<String, Object> properties = (Map<String, Object>) data.get(Constants.PROPERTIES);
            if (properties.containsKey(Constants.AI_METADATA)) {
                metadata.put(Constants.AI_METADATA, properties.get(Constants.AI_METADATA));
            }
            if (properties.containsKey(Constants.AI_TOKEN_USAGE)) {
                metadata.put(Constants.AI_TOKEN_USAGE, properties.get(Constants.AI_TOKEN_USAGE));
            }
            if (properties.containsKey(Constants.IS_EGRESS)) {
                metadata.put(Constants.IS_EGRESS, properties.get(Constants.IS_EGRESS));
            }
            if (properties.containsKey(Constants.SUBTYPE)) {
                metadata.put(Constants.SUBTYPE, properties.get(Constants.SUBTYPE));
            }
        }
    }
}
