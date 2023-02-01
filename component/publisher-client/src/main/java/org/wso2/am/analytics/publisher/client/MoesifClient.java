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

<<<<<<< HEAD
=======
import com.google.gson.Gson;
>>>>>>> origin/moesif-new
import com.moesif.api.MoesifAPIClient;
import com.moesif.api.controllers.APIController;
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import com.moesif.api.http.response.HttpResponse;
import com.moesif.api.models.EventBuilder;
import com.moesif.api.models.EventModel;
import com.moesif.api.models.EventRequestBuilder;
import com.moesif.api.models.EventRequestModel;
import com.moesif.api.models.EventResponseBuilder;
import com.moesif.api.models.EventResponseModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.MissedEventHandler;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;
import org.wso2.am.analytics.publisher.util.Constants;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moesif Client is responsible for sending events to
 * Moesif Analytics Dashboard.
 */
public class MoesifClient {
    private final Logger log = LoggerFactory.getLogger(MoesifClient.class);
    private final MoesifKeyRetriever keyRetriever;
    private final MissedEventHandler missedEventHandler;

    public MoesifClient(MoesifKeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
        this.missedEventHandler = new MissedEventHandler(keyRetriever);
        // execute MissedEventHandler periodically.
        Timer timer = new Timer();
        timer.schedule(missedEventHandler, 0, MoesifMicroserviceConstants.PERIODIC_CALL_DELAY);
    }

    private void doRetry(String orgId, MetricEventBuilder builder) {
        Integer currentAttempt = MoesifClientContextHolder.PUBLISH_ATTEMPTS.get();

        if (currentAttempt > 0) {
<<<<<<< HEAD
=======
            if (currentAttempt == MoesifMicroserviceConstants.NUM_RETRY_ATTEMPTS_PUBLISH) {
                // on failure remove the respective moesif key from the map.
                // this will result in a call to the microservice to retrieve the key.
                // This is enough to happen only at the first attempt.
                keyRetriever.removeMoesifKeyFromMap(orgId);
            }
>>>>>>> origin/moesif-new
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
<<<<<<< HEAD
            log.error("Failed all retrying attempts. Event will be dropped for organization {}", orgId);
=======
            log.error("Failed all retrying attempts. Event will be dropped");
>>>>>>> origin/moesif-new
        }
    }

    /**
     * publish method is responsible for checking the availability of relevant moesif key
     * and initiating moesif client sdk.
     */
    public void publish(MetricEventBuilder builder) throws MetricReportingException {
        Map<String, Object> event = builder.build();
        ConcurrentHashMap<String, String> orgIDMoesifKeyMap = keyRetriever.getMoesifKeyMap();

        String orgId = (String) event.get(Constants.ORGANIZATION_ID);
        String moesifKey;
        if (orgIDMoesifKeyMap.containsKey(orgId)) {
            moesifKey = orgIDMoesifKeyMap.get(orgId);
        } else {
            // store events with orgID that misses moesif keys,
            // in the internal map inside a queueMissed.
            // call the microservice when the scheduled time reaches.
            // put the elements in queue missed to eventQueue.
            missedEventHandler.putMissedEvent(builder);
            return;
        }

        // init moesif api client
        MoesifAPIClient client = keyRetriever.getMoesifClient(moesifKey);
        APIController api = client.getAPI();

        APICallBack<HttpResponse> callBack = new APICallBack<HttpResponse>() {
            public void onSuccess(HttpContext context, HttpResponse response) {
                int statusCode = context.getResponse().getStatusCode();
                if (statusCode == 200) {
                    log.debug("Event successfully published.");
                } else if (statusCode >= 400 && statusCode < 500) {
<<<<<<< HEAD
                    log.error("Event publishing failed for organization: {}. Moesif returned {}.", orgId, statusCode);
                } else {
                    log.error("Event publishing failed for organization: {}. Moesif returned {}.", orgId, statusCode);
=======
                    log.error("Event publishing failed. Moesif returned " + statusCode);
                } else {
                    log.error("Event publishing failed.Retrying.");
>>>>>>> origin/moesif-new
                    doRetry(orgId, builder);
                }
            }

            public void onFailure(HttpContext context, Throwable error) {
                int statusCode = context.getResponse().getStatusCode();

                if (statusCode >= 400 && statusCode < 500) {
<<<<<<< HEAD
                    log.error("Event publishing failed for organization: {}. Moesif returned {}.", orgId, statusCode);
                } else if (error != null) {
                    log.error("Event publishing failed for organization: {}. Event publishing failed.", orgId,
                            error);
                } else {
                    log.error("Event publishing failed for organization: {}. Retrying.", orgId);
=======
                    log.error("Event publishing failed. Moesif returned:", statusCode);
                } else if (error != null) {
                    log.error("Event publishing failed.", error);
                } else {
                    log.error("Event publishing failed. Retrying.");
>>>>>>> origin/moesif-new
                    doRetry(orgId, builder);
                }

            }
        };
        try {
            api.createEventAsync(buildEventResponse(event), callBack);
        } catch (IOException e) {
<<<<<<< HEAD
            log.error("Analytics event sending failed. Event will be dropped.Failed for organization: {}", orgId, e);
=======
            log.error("Analytics event sending failed. Event will be dropped", e);
>>>>>>> origin/moesif-new
        }

    }

    private EventModel buildEventResponse(Map<String, Object> data) throws IOException, MetricReportingException {
<<<<<<< HEAD
=======
        Gson gson = new Gson();
        String jsonString = gson.toJson(data);
        String reqBody = jsonString.replaceAll("[\r\n]", "");

>>>>>>> origin/moesif-new
        //      Preprocessing data
        final URL uri = new URL((String) data.get(Constants.DESTINATION));
        final String hostName = uri.getHost();

        final String userIP = (String) data.get(Constants.USER_IP);

        Map<String, String> reqHeaders = new HashMap<String, String>();

<<<<<<< HEAD
        reqHeaders.put(Constants.MOESIF_USER_AGENT_KEY,
                (String) data.getOrDefault(Constants.USER_AGENT_HEADER, Constants.UNKNOWN_VALUE));
        reqHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, Constants.MOESIF_CONTENT_TYPE_HEADER);
=======
        reqHeaders.put("User-Agent",
                (String) data.getOrDefault(Constants.USER_AGENT_HEADER, Constants.UNKNOWN_VALUE));
        reqHeaders.put("Content-Type", Constants.MOESIF_CONTENT_TYPE_HEADER);
>>>>>>> origin/moesif-new
        reqHeaders.put("Host", hostName);

        Map<String, String> rspHeaders = new HashMap<String, String>();

        rspHeaders.put("Vary", "Accept-Encoding");
        rspHeaders.put("Pragma", "no-cache");
        rspHeaders.put("Expires", "-1");
<<<<<<< HEAD
        rspHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, "application/json; charset=utf-8");
=======
        rspHeaders.put("Content-Type", "application/json; charset=utf-8");
>>>>>>> origin/moesif-new
        rspHeaders.put("Cache-Control", "no-cache");


        EventRequestModel eventReq = new EventRequestBuilder()
<<<<<<< HEAD
                .time(new Date())
=======
                .time(new Date()) // See if you can parse request time stamp to date obj
>>>>>>> origin/moesif-new
                .uri(uri.toString())
                .verb((String) data.get(Constants.API_METHOD))
                .apiVersion((String) data.get(Constants.API_VERSION))
                .ipAddress(userIP)
                .headers(reqHeaders)
<<<<<<< HEAD
=======
                .body(reqBody)
>>>>>>> origin/moesif-new
                .build();


        EventResponseModel eventRsp = new EventResponseBuilder()
                .time(new Date(System.currentTimeMillis() + 1000))
                .status((int) data.get(Constants.TARGET_RESPONSE_CODE))
                .headers(rspHeaders)
                .build();

        EventModel eventModel = new EventBuilder()
                .request(eventReq)
                .response(eventRsp)
                .userId((String) data.get("userName"))
                .companyId((String) data.get(Constants.ORGANIZATION_ID))
                .build();

        return eventModel;
    }

}
