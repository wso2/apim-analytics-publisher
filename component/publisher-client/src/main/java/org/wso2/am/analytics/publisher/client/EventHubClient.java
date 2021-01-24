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
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.implementation.EventHubSharedKeyCredential;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;

/**
 * Event Hub client is responsible for sending events to
 * Azure Event Hub.
 */
public class EventHubClient {
    private static final Logger log = Logger.getLogger(EventHubClient.class);
    private EventHubProducerClient producer;

    public EventHubClient(String sasToken) {
        if (null == sasToken || sasToken.isEmpty()) {
            sasToken = System.getenv("API_ANL_SAS_TOKEN");
            if (sasToken == null || sasToken.isEmpty()) {
                log.error("SAS Token is not provided. Publisher can not be initialized");
                return;
            }
        }
        TokenCredential tokenCredential = new EventHubSharedKeyCredential(sasToken);
        String resourceURI = getResourceURI(sasToken);
        String fullyQualifiedNamespace = resourceURI.split("/")[0];
        String eventhubName = resourceURI.split("/", 2)[1];
        producer = new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventhubName, tokenCredential)
                .buildProducerClient();
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

    public void sendEvent(String event) {
        //will implement batching in next milestone
        EventData eventData = new EventData(event);
        producer.send(Collections.singleton(eventData));
        log.debug("Published a single message to Analytics cluster. " + event.replaceAll("[\r\n]", ""));
    }
}
