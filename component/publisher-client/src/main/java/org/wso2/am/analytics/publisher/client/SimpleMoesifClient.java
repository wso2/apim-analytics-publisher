/**
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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

import com.moesif.api.BodyParser;
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
import org.apache.commons.lang3.StringUtils;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.MoesifRetryBuffer;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.RetryConfig;
import org.wso2.am.analytics.publisher.reporter.moesif.sampling.MoesifSamplingManager;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.HttpStatusHelper;
import org.wso2.am.analytics.publisher.util.LogSanitizer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final String moesifKey;
    private final MoesifSamplingManager samplingManager;
    private final MoesifRetryBuffer retryBuffer;

    public SimpleMoesifClient(String key) {
        this(key, null, null, null);
    }

    public SimpleMoesifClient(String key, String url) {
        this(key, url, null, null);
    }

    public SimpleMoesifClient(String key, String url, MoesifSamplingManager samplingManager) {
        this(key, url, samplingManager, null);
    }

    public SimpleMoesifClient(String key, String url, MoesifSamplingManager samplingManager,
                              RetryConfig retryConfig) {
        this.moesifAPIClient = (url == null || url.isEmpty())
                ? new MoesifAPIClient(key)
                : new MoesifAPIClient(key, url);
        this.api = moesifAPIClient.getAPI();
        this.moesifKey = key;
        this.samplingManager = samplingManager;
        if (samplingManager != null) {
            samplingManager.register(key, moesifAPIClient);
        }
        this.retryBuffer = retryConfig == null
                ? null
                : new MoesifRetryBuffer(key, moesifAPIClient, retryConfig);
    }

    @Override
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();

        EventModel eventModel;
        try {
            eventModel = buildEventResponse(event);
        } catch (IOException e) {
            log.error("Analytics event sending failed. Event will be dropped", e);
            return;
        }
        if (!applySampling(eventModel)) {
            return;
        }

        List<EventModel> singletonBatch = new ArrayList<>(1);
        singletonBatch.add(eventModel);
        sendOrStash(singletonBatch, "Single event");
    }

    @Override
    public void publishBatch(List<MetricEventBuilder> builders) {
        if (builders == null || builders.isEmpty()) {
            return;
        }
        List<EventModel> events = buildEventsFromBuilders(builders);

        List<EventModel> sampled = new ArrayList<>(events.size());
        for (EventModel e : events) {
            if (applySampling(e)) {
                sampled.add(e);
            }
        }
        if (sampled.isEmpty()) {
            return;
        }
        sendOrStash(sampled, "Batch of " + sampled.size() + " events");
    }

    /**
     * Sends the batch via the Moesif SDK, or stashes it in the retry buffer when configured
     * and Moesif is currently unhealthy. Retryable failures from the callback also feed the
     * buffer instead of recursing through the SDK callback thread.
     */
    private void sendOrStash(List<EventModel> batch, String operationType) {
        if (retryBuffer != null && !retryBuffer.isHealthy()) {
            retryBuffer.stash(batch);
            return;
        }
        APICallBack<HttpResponse> callBack = createMoesifCallback(batch, operationType);
        try {
            if (batch.size() == 1) {
                api.createEventAsync(batch.get(0), callBack);
            } else {
                api.createEventsBatchAsync(batch, callBack);
            }
        } catch (IOException e) {
            if (retryBuffer != null) {
                log.debug("{} could not be sent now; queueing for retry", operationType, e);
                retryBuffer.onRetryableFailure(batch);
            } else {
                log.error("Failed to send analytics events; events will be dropped", e);
            }
        }
    }

    /**
     * Applies the dynamic sampling decision: returns false if the event should be dropped, otherwise stamps
     * the event's weight so Moesif can extrapolate metrics correctly. When sampling is disabled, returns true
     * with no weight change.
     */
    private boolean applySampling(EventModel event) {
        if (samplingManager == null || !samplingManager.isEnabled()) {
            return true;
        }
        if (!samplingManager.shouldSend(moesifKey, event)) {
            return false;
        }
        event.setWeight(samplingManager.weightFor(moesifKey, event));
        return true;
    }

    @Override
    public EventModel buildEventResponse(Map<String, Object> data) throws IOException {
        Map<String, String> reqHeaders = new HashMap<>();
        Map<String, String> rspHeaders = new HashMap<>();

        populateHeaders(data, reqHeaders, rspHeaders);

        // Override the headers-map Content-Type with the captured body's real media type so Moesif can
        // label/parse the body even when send_headers is off (populateHeaders otherwise defaults it to
        // application/json). Done before resolveBody so BodyParser's JSON detection also sees the right
        // type. Applied only when a body is actually present, so no-body events keep their headers.
        applyBodyContentType(data, reqHeaders, Constants.REQUEST_BODY, Constants.REQUEST_CONTENT_TYPE);
        applyBodyContentType(data, rspHeaders, Constants.RESPONSE_BODY, Constants.RESPONSE_CONTENT_TYPE);

        // Resolve request/response bodies (and their transfer encodings) from properties, removing them
        // so they are not also duplicated into metadata by populateAIInfo's metadata.putAll(properties).
        BodyParser.BodyWrapper requestBody = resolveBody(data, reqHeaders,
                Constants.REQUEST_BODY, Constants.REQUEST_BODY_TRANSFER_ENCODING);
        BodyParser.BodyWrapper responseBody = resolveBody(data, rspHeaders,
                Constants.RESPONSE_BODY, Constants.RESPONSE_BODY_TRANSFER_ENCODING);

        EventRequestModel eventReq;
        EventResponseModel eventRsp;
        EventModel eventModel = new EventModel();
        String modifiedUserName;

        Map<String, Object> metadata = new HashMap<>();
        populateMetadata(data, metadata);

        modifiedUserName = sanitizeUserName(data);
        if (!data.containsKey(Constants.ERROR_CODE)) {
            final String userIP = (String) data.get(Constants.USER_IP);
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
                    .ipAddress(userIP).headers(reqHeaders)
                    .body(requestBody.body).transferEncoding(requestBody.transferEncoding).build();

            eventRsp = new EventResponseBuilder().time(Date.from(responseTimestamp))
                    .status((int) data.get(Constants.PROXY_RESPONSE_CODE)).headers(rspHeaders)
                    .body(responseBody.body).transferEncoding(responseBody.transferEncoding).build();

        } else {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
            Instant requestTimestamp = Instant.from(
                    dateTimeFormatter.parse((String) data.get(Constants.REQUEST_TIMESTAMP)));

            String verb = (String) data.get(Constants.API_METHOD);
            if (StringUtils.isEmpty(verb)) {
                verb = Constants.NOT_APPLICABLE;
            }

            String uri = Constants.NOT_APPLICABLE;
            String apiResourceTemplate = (String) data.get(Constants.API_RESOURCE_TEMPLATE);
            LinkedHashMap properties = (LinkedHashMap) data.get(Constants.PROPERTIES);
            String apiContext = (String) properties.get(Constants.API_CONTEXT);
            String gwURL = (String) properties.get(Constants.GATEWAY_URL);
            if (StringUtils.isNotEmpty(gwURL)) {
                uri = gwURL;
            } else if (StringUtils.isNotEmpty(apiContext) 
                && StringUtils.isNotEmpty(apiResourceTemplate)) {
                uri = apiContext + apiResourceTemplate;
            }

            eventReq = new EventRequestBuilder().time(Date.from(requestTimestamp)).uri(uri)
                    .verb(verb).apiVersion((String) data.get(Constants.API_VERSION))
                    .headers(reqHeaders)
                    .body(requestBody.body).transferEncoding(requestBody.transferEncoding).build();

            Date dateNow = Date.from(Instant.now());

            eventRsp = new EventResponseBuilder().time(dateNow).status((int) data.get(Constants.PROXY_RESPONSE_CODE))
                    .headers(rspHeaders)
                    .body(responseBody.body).transferEncoding(responseBody.transferEncoding).build();
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
                Constants.REQUEST_MEDIATION_LATENCY, Constants.API_RESOURCE_TEMPLATE, Constants.RESPONSE_LATENCY

        ));

        data.entrySet().stream().filter(entry -> requiredKeys.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));

        // Add AI metadata, token usage and MCP info if present
        populateAIInfo(data, metadata);

    }

    private APICallBack<HttpResponse> createMoesifCallback(List<EventModel> batch, String operationType) {
        return new APICallBack<HttpResponse>() {
            @Override
            public void onSuccess(HttpContext httpContext, HttpResponse response) {
                try {
                    int statusCode = httpContext.getResponse().getStatusCode();
                    if (HttpStatusHelper.isSuccess(statusCode)) {
                        log.debug("{} successfully published. Status: {}", operationType, statusCode);
                        if (retryBuffer != null) {
                            retryBuffer.onSuccess();
                        }
                    } else if (HttpStatusHelper.shouldRetry(statusCode)) {
                        handleRetryable(statusCode);
                    } else {
                        log.error("{} publishing failed. Moesif returned {}. Response: {}. No retry.",
                                operationType,
                                LogSanitizer.sanitize(String.valueOf(statusCode)),
                                response.getRawBody());
                    }
                } catch (Throwable t) {
                    log.error("Unexpected error after sending {} to Moesif", operationType, t);
                }
            }

            @Override
            public void onFailure(HttpContext httpContext, Throwable error) {
                try {
                    int statusCode = httpContext != null && httpContext.getResponse() != null
                            ? httpContext.getResponse().getStatusCode()
                            : 0;
                    String errorMessage = error != null ? error.getMessage() : "Unknown error";

                    if (statusCode == 0 || HttpStatusHelper.shouldRetry(statusCode)) {
                        handleRetryable(statusCode);
                    } else if (HttpStatusHelper.isClientError(statusCode)) {
                        log.error("{} publishing failed. Moesif returned {} due to error: {}",
                                operationType,
                                statusCode,
                                LogSanitizer.sanitize(errorMessage));
                    } else {
                        log.error("{} publishing failed due to error: {}",
                                operationType,
                                LogSanitizer.sanitize(errorMessage));
                    }
                } catch (Throwable t) {
                    log.error("Unexpected error while handling {} send failure", operationType, t);
                }
            }

            private void handleRetryable(int statusCode) {
                if (retryBuffer != null) {
                    retryBuffer.onRetryableFailure(batch);
                } else {
                    log.error("Failed to send {} (status {}); retry queue disabled, events will be dropped",
                            operationType, LogSanitizer.sanitize(String.valueOf(statusCode)));
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
                metadata.put(Constants.AI_METADATA, properties.remove(Constants.AI_METADATA));
            }
            if (properties.containsKey(Constants.AI_TOKEN_USAGE)) {
                metadata.put(Constants.AI_TOKEN_USAGE, properties.remove(Constants.AI_TOKEN_USAGE));
            }
            if (properties.containsKey(Constants.IS_EGRESS)) {
                metadata.put(Constants.IS_EGRESS, properties.remove(Constants.IS_EGRESS));
            }
            if (properties.containsKey(Constants.SUBTYPE)) {
                metadata.put(Constants.SUBTYPE, properties.remove(Constants.SUBTYPE));
            }
            if (properties.containsKey(Constants.IS_GUARDRAIL_HIT)) {
                metadata.put(Constants.IS_GUARDRAIL_HIT, properties.remove(Constants.IS_GUARDRAIL_HIT));
            }
            if (properties.containsKey(Constants.GUARDRAIL_NAME)) {
                metadata.put(Constants.GUARDRAIL_NAME, properties.remove(Constants.GUARDRAIL_NAME));
            }
            if (properties.containsKey(Constants.MCP_ANALYTICS)) {
                if (log.isDebugEnabled()) {
                    log.debug("MCP analytics data found and transferred to metadata");
                }
                metadata.put(Constants.MCP_ANALYTICS, properties.remove(Constants.MCP_ANALYTICS));
            }
            metadata.putAll(properties);
        }
    }

    /**
     * Removes and returns a body value ({@code requestBody}/{@code responseBody}) from the event's
     * nested {@code properties} map. Removing it prevents the raw body from also being copied into
     * Moesif metadata by {@link #populateAIInfo}.
     *
     * @param data the event data map
     * @param key  the body property key
     * @return the raw body value, or {@code null} if absent
     */
    private Object extractAndRemoveBody(Map<String, Object> data, String key) {
        Object propertiesObj = data.get(Constants.PROPERTIES);
        if (propertiesObj instanceof Map) {
            return ((Map<String, Object>) propertiesObj).remove(key);
        }
        return null;
    }

    /**
     * When a body is present in the event's {@code properties}, sets the given headers map's
     * {@code Content-Type} to the captured body's media type (property {@code contentTypeKey}), so Moesif
     * renders the body correctly regardless of {@code send_headers}. Only real media types (containing
     * "/") override the default; the content-type property is left in {@code properties} as metadata.
     *
     * @param data           the event data map
     * @param headers        the request/response headers map to update
     * @param bodyKey        the body property key (presence gates the override)
     * @param contentTypeKey the content-type property key
     */
    private void applyBodyContentType(Map<String, Object> data, Map<String, String> headers,
                                      String bodyKey, String contentTypeKey) {
        Object propertiesObj = data.get(Constants.PROPERTIES);
        if (!(propertiesObj instanceof Map)) {
            return;
        }
        Map<String, Object> properties = (Map<String, Object>) propertiesObj;
        Object body = properties.get(bodyKey);
        if (!(body instanceof String) || ((String) body).isEmpty()) {
            return;
        }
        Object contentType = properties.get(contentTypeKey);
        if (contentType instanceof String && ((String) contentType).contains("/")) {
            headers.put(Constants.MOESIF_CONTENT_TYPE_KEY, ((String) contentType).trim());
        }
    }

    /**
     * Resolves a captured body into a Moesif {@link BodyParser.BodyWrapper} (body + transferEncoding),
     * removing both the body and its transfer-encoding hint from the event's nested {@code properties}
     * map so they are not also copied into Moesif metadata by {@link #populateAIInfo}.
     *
     * <p>Binary payloads are pre-encoded as Base64 by the gateway and passed through with
     * {@code transferEncoding=base64}. JSON/text payloads are handed to Moesif's {@link BodyParser},
     * which renders JSON as a structured object and Base64-encodes anything else (the Moesif standard).</p>
     *
     * @param data        the event data map
     * @param headers     the request/response headers (used by BodyParser to detect JSON)
     * @param bodyKey     the body property key
     * @param encodingKey the transfer-encoding hint property key
     * @return a body wrapper; {@code body}/{@code transferEncoding} are {@code null} when no body captured
     */
    private BodyParser.BodyWrapper resolveBody(Map<String, Object> data, Map<String, String> headers,
                                               String bodyKey, String encodingKey) {
        Object encoding = extractAndRemoveBody(data, encodingKey);
        Object body = extractAndRemoveBody(data, bodyKey);
        if (!(body instanceof String) || ((String) body).isEmpty()) {
            return new BodyParser.BodyWrapper(null, null);
        }
        String bodyString = (String) body;
        if (Constants.TRANSFER_ENCODING_BASE64.equals(encoding)) {
            // Binary: already Base64-encoded by the gateway; pass through verbatim.
            return new BodyParser.BodyWrapper(bodyString, Constants.TRANSFER_ENCODING_BASE64);
        }
        // JSON/text: Moesif standard - structured JSON object, otherwise Base64.
        return BodyParser.parseBody(headers, bodyString);
    }

    /**
     * Sanitizes the username by removing the @carbon.super suffix.
     * Extracts username from data map or properties, then removes the @carbon.super suffix if present.
     *
     * @param data The data map containing user information.
     * @return The sanitized username or `Constants.UNKNOWN_VALUE` if no username is found
     */
    private String sanitizeUserName(Map<String, Object> data) {
        String sanitizedUserName = "";
        String userName = (String) data.get(Constants.USER_NAME);
        Object propertiesObj = data.get(Constants.PROPERTIES);

        if (StringUtils.isNotEmpty(userName)) {
            sanitizedUserName = userName;
            if (log.isDebugEnabled()) {
                log.debug("Using userName from data: {}", userName);
            }
        } else if (propertiesObj instanceof LinkedHashMap) {
            // We've confirmed it's a LinkedHashMap, but we still need to
            // suppress the warning for the *generic* part (<String, Object>),
            // which `instanceof` cannot check.
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> properties = (LinkedHashMap<String, Object>) propertiesObj;
            String propUserName = (String) properties.get(Constants.USER_NAME);
            if (propUserName != null) {
                sanitizedUserName = propUserName;
                if (log.isDebugEnabled()) {
                    log.debug("Using userName from properties: {}", propUserName);
                }
            }
        }

        if (sanitizedUserName.endsWith(Constants.CARBON_SUPER_SUFFIX)) {
            return sanitizedUserName.substring(0,
                    sanitizedUserName.length() - Constants.CARBON_SUPER_SUFFIX.length());
        }

        if (sanitizedUserName.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No username found, returning UNKNOWN_VALUE");
            }
            return Constants.UNKNOWN_VALUE;
        }
        return sanitizedUserName;
    }
}
