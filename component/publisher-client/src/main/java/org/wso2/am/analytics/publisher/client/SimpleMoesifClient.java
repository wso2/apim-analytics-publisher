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

    public SimpleMoesifClient(String key) {
        this.moesifAPIClient = new MoesifAPIClient(key);
        this.api = moesifAPIClient.getAPI();
    }

    @Override
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();

        APICallBack<HttpResponse> callBack = new APICallBack<HttpResponse>() {
            /**
             * Callback method invoked when the event publishing request receives a response from Moesif.
             * This method handles the response based on the HTTP status code:
             *   If the status code is 200, 201, 202, or 204, the event is considered successfully
             *   published and a debug log is written.
             *   If the status code is in the 4xx range, it logs an error indicating a client-side failure.
             *   For all other status codes (typically 5xx or unexpected values), it logs the error and initiates a
             *   retry using {@code doRetry(builder)}.
             *
             * @param context  the HTTP context containing metadata about the request and response
             * @param response the HTTP response received from the Moesif API
             */

            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                if (statusCode == 200 || statusCode == 201 || statusCode == 202 || statusCode == 204) {
                    log.debug("Event successfully published.");
                } else if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else {
                    log.error("Event publishing failed. Retrying ..");
                    doRetry(builder);
                }
            }
            /**
             * Callback method invoked when the event publishing to Moesif fails.
             *
             * This method handles the failure response based on the HTTP status code and any thrown exception.
             * If the response status code is in the 4xx range, it logs an error and does not retry,
             * since these are considered client-side errors (e.g., 401 Unauthorized, 404 Not Found).
             * If an exception occurred (e.g., network error), it logs the failure without retrying.
             * If there is no exception and the status code is not in the 4xx range, it assumes a
             * retryable failure (e.g., server error or network timeout) and attempts to retry the event publishing.
             *
             * @param context the HTTP context containing the response from the Moesif API
             * @param error   the Throwable indicating the cause of the failure, or {@code null}
             *               if no exception occurred
             */
            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();

                if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else if (error != null) {
                    log.error("Event publishing failed");
                } else {
                    log.error("Event publishing failed. Retrying.");
                    doRetry(builder);
                }
            }
        };
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
        List<EventModel> events = new ArrayList<>();
        for (MetricEventBuilder builder : builders) {
            try {
                Map<String, Object> event = builder.build();
                events.add(buildEventResponse(event));
            } catch (MetricReportingException | IOException e) {
                log.error("Builder instance is not duly filled. Event building failed", e);
            }
        }

        APICallBack<HttpResponse> callBack = new APICallBack<HttpResponse>() {
            /**
             * Callback method invoked when the event publishing request receives a response from Moesif.
             * This method handles the response based on the HTTP status code:
             *   If the status code is 200, 201, 202, or 204, the event is considered successfully
             *   published and a debug log is written.
             *   If the status code is in the 4xx range, it logs an error indicating a client-side failure.
             *   For all other status codes (typically 5xx or unexpected values), it logs the error and initiates a
             *   retry using {@code doRetry(builder)}.
             *
             * @param context  the HTTP context containing metadata about the request and response
             * @param response the HTTP response received from the Moesif API
             */

            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                if (statusCode == 200 || statusCode == 201 || statusCode == 202 || statusCode == 204) {
                    log.debug("Event successfully published.");
                } else if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else {
                    log.error("Event publishing failed. Retrying ..");
                    doRetry(builders);
                }
            }
            /**
             * Callback method invoked when the event publishing to Moesif fails.
             *
             * This method handles the failure response based on the HTTP status code and any thrown exception.
             * If the response status code is in the 4xx range, it logs an error and does not retry,
             * since these are considered client-side errors (e.g., 401 Unauthorized, 404 Not Found).
             * If an exception occurred (e.g., network error), it logs the failure without retrying.
             * If there is no exception and the status code is not in the 4xx range, it assumes a
             * retryable failure (e.g., server error or network timeout) and attempts to retry the event publishing.
             *
             * @param context the HTTP context containing the response from the Moesif API
             * @param error   the Throwable indicating the cause of the failure, or {@code null}
             *               if no exception occurred
             */
            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();

                if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else if (error != null) {
                    log.error("Event publishing failed");
                } else {
                    log.error("Event publishing failed. Retrying.");
                    doRetry(builders);
                }
            }
        };
        try {
            api.createEventsBatchAsync(events, callBack);
        } catch (IOException e) {
            log.error("Analytics event sending failed. Event will be dropped", e);
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

        Map<String, String> metadata = new HashMap<>();
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
                    .status((int) data.get(Constants.TARGET_RESPONSE_CODE)).headers(rspHeaders).build();

            if (userName.contains("@carbon.super")) {
                modifiedUserName = userName.replace("@carbon.super", "");
            } else {
                modifiedUserName = userName;
            }

        } else {
            LinkedHashMap properties = (LinkedHashMap) data.get(Constants.PROPERTIES);

            modifiedUserName = (String) data.get(Constants.API_CREATION);

            String apiContext = (String) ((LinkedHashMap) data.get(Constants.PROPERTIES)).get(Constants.API_CONTEXT);
            String gwURL = (String) properties.get(Constants.GATEWAY_URL);
            String apiResourceTemplate = (String) data.get(Constants.API_RESOURCE_TEMPLATE);
            String uri = apiContext + apiResourceTemplate;

            if (gwURL != null) {
                uri = gwURL;
            }

            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
            Instant requestTimestamp = Instant.from(
                    dateTimeFormatter.parse((String) data.get(Constants.REQUEST_TIMESTAMP)));

            eventReq = new EventRequestBuilder().time(Date.from(requestTimestamp)).uri(uri)
                    .verb((String) data.get(Constants.API_METHOD)).apiVersion((String) data.get(Constants.API_VERSION))
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
    private void populateMetadata(Map<String, Object> data, Map<String, String> metadata) {
        Set<String> requiredKeys = new HashSet<>(Arrays.asList(
                Constants.API_ID, Constants.API_METHOD, Constants.API_NAME,
                Constants.API_TYPE, Constants.APPLICATION_ID, Constants.APPLICATION_NAME, Constants.APPLICATION_OWNER,
                Constants.BACKEND_LATENCY, Constants.GATEWAY_TYPE, Constants.KEY_TYPE, Constants.EVENT_TYPE,
                Constants.DESTINATION, Constants.ERROR_CODE, Constants.ERROR_MESSAGE, Constants.ERROR_TYPE
        ));

        data.entrySet().stream().filter(entry -> requiredKeys.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> metadata.put(entry.getKey(), String.valueOf(entry.getValue())));

    }

    /**
     * Retries publishing the event using the provided `MetricEventBuilder` if retry attempts are available.
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
}
