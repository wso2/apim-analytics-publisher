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
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Event Hub client is responsible for sending events to
 * Azure Event Hub.
 */
public class EventHubClient {
    private static final Logger log = Logger.getLogger(EventHubClient.class);
    private EventHubProducerClient producer;
    private EventDataBatch batch;
    private ReadWriteLock readWriteLock;
    private Semaphore sendSemaphore;

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
        batch = producer.createBatch();
        readWriteLock = new ReentrantReadWriteLock();
        sendSemaphore = new Semaphore(1);
    }

    /**
     * Extracts the resource URI from the SAS Token
     *
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
        EventData eventData = new EventData(event);
        readWriteLock.readLock().lock();
        try {
            if (!batch.tryAdd(eventData)) {
                try {
                    sendSemaphore.acquire();
                    if (!batch.tryAdd(eventData)) {
                        int size = batch.getCount();
                        producer.send(batch);
                        batch = producer.createBatch();
                        batch.tryAdd(eventData);
                        log.debug("Published " + size + "events to Analytics cluster.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    sendSemaphore.release();
                }
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void flushEvents() {
        try {
            sendSemaphore.acquire();
            int size = batch.getCount();
            producer.send(batch);
            batch = producer.createBatch();
            log.debug("Flushed " + size + "events to Analytics cluster.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sendSemaphore.release();
        }
    }
}
