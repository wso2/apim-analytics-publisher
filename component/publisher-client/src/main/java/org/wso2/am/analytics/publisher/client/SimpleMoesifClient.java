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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                if (statusCode == 200 || statusCode == 201 || statusCode == 202 || statusCode == 204) {
                    log.debug("Event successfully published.");
                } else if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else {
                    log.error("Event publishing failed. Retrying.");
                    doRetry(builder);
                }
            }

            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();

                if (statusCode >= 400 && statusCode < 500) {
                    log.error("Event publishing failed. Moesif returned {}.",
                            String.valueOf(statusCode).replaceAll("[\r\n]", ""));
                } else if (error != null) {
                    log.error("Event publishing failed. Event publishing failed.");
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

    private void doRetry(MetricEventBuilder builder) {
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
            log.error("Failed all retrying attempts. Event will be dropped");
        }
    }

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
}
