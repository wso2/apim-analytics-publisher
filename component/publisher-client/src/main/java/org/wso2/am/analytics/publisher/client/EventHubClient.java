/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.client;

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.implementation.EventHubSharedKeyCredential;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Event Hub client is responsible for sending provided number of events to
 * Azure Event Hub.
 */
public class EventHubClient implements Runnable {
    private static final Logger log = Logger.getLogger(EventHubClient.class);
    private EventHubProducerClient producer;
    private int eventCount;
    private EventDataBatch batch;

    public EventHubClient(String sasToken, int eventCount) {
        this.eventCount = eventCount;
        TokenCredential tokenCredential = new EventHubSharedKeyCredential(sasToken);
        String resourceURI = getResourceURI(sasToken);
        String fullyQualifiedNamespace = resourceURI.split("/")[0];
        String eventhubName = resourceURI.split("/", 2)[1];
        producer = new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventhubName, tokenCredential)
                .buildProducerClient();
        batch = producer.createBatch();
    }

    /**
     * Extracts the resource URI from the SAS Token
     * @param sasToken SAS token of the user
     * @return decoded resource URI from the token
     */
    private String getResourceURI(String sasToken) {
        String[] sasAttributes = sasToken.split("&");
        String[] resource = sasAttributes[0].split("=");
        String resourceURI = "";
        try {
            resourceURI = URLDecoder.decode(resource[1], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            //never happens
        }
        //remove protocol append
        return resourceURI.replace("sb://", "");
    }

    @Override public void run() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(tz);
        for (int i = 0; i < eventCount; i++) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("requestTimestamp", df.format(new Date()));
            jsonObject.addProperty("correlationId", String.valueOf(UUID.randomUUID()));
            jsonObject.addProperty("keyType", DataHolder.KEY_TYPE[i % 2]);
            jsonObject.addProperty("deploymentId", DataHolder.DEPLOYMENT_ID[i % 5]);
            jsonObject.addProperty("apiId", DataHolder.API_UUID[i % 5]);
            jsonObject.addProperty("gatewayType", DataHolder.GATEWAY_TYPE[i % 5]);
            jsonObject.addProperty("destination", DataHolder.DESTINATION[i % 5]);
            jsonObject.addProperty("requestMediationLatency", DataHolder.REQUEST_MED_LATENCY[i % 5]);
            jsonObject.addProperty("responseMediationLatency", DataHolder.RESPONSE_MED_LATENCY[i % 5]);
            jsonObject.addProperty("responseCode", DataHolder.RESPONSE_CODE[i % 5]);
            jsonObject.addProperty("responseSize", DataHolder.RESPONSE_SIZE[i % 5]);
            jsonObject.addProperty("responseLatency", DataHolder.RESPONSE_LATENCY[i % 5]);
            jsonObject.addProperty("apiCreator", DataHolder.API_CREATOR[i % 5]);
            jsonObject.addProperty("apiMethod", DataHolder.API_METHOD[i % 5]);
            jsonObject.addProperty("apiResourceTemplate", DataHolder.API_RESOURCE_TEMPLATE[i % 5]);
            jsonObject.addProperty("apiVersion", DataHolder.API_VERSION[i % 5]);
            jsonObject.addProperty("apiName", DataHolder.API_NAME[i % 5]);
            jsonObject.addProperty("apiContext", DataHolder.API_CONTEXT[i % 5]);
            jsonObject.addProperty("apiCreatorTenantDomain", DataHolder.API_CREATOR_TENANT_DOMAIN[i % 5]);
            jsonObject.addProperty("applicationName", DataHolder.APPLICATION_NAME[i % 5]);
            jsonObject.addProperty("applicationId", DataHolder.NODE_ID[i % 5]);
            jsonObject.addProperty("applicationConsumerKey", DataHolder.APPLICATION_CONSUMER_KEY[i % 5]);
            jsonObject.addProperty("applicationOwner", DataHolder.APPLICATION_OWNER[i % 5]);
            jsonObject.addProperty("regionId", DataHolder.REGION_ID[i % 5]);
            jsonObject.addProperty("userAgent", DataHolder.USER_AGENT[i % 5]);
            jsonObject.addProperty("eventType", DataHolder.EVENT_TYPE[i % 5]);
            String payload = jsonObject.toString();
            EventData eventData = new EventData(payload);
            if (!batch.tryAdd(eventData)) {
                log.info(("Sending Events: " + batch.getCount()).replaceAll("[\r\n]", ""));
                producer.send(batch);
                batch = producer.createBatch();
                batch.tryAdd(eventData);
            }
        }
        log.info(("Sending Events: " + batch.getCount()).replaceAll("[\r\n]", ""));
        producer.send(batch);
    }
}
