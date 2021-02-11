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

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.ConnectionRecoverableException;
import org.wso2.am.analytics.publisher.exception.ConnectionUnrecoverableException;

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
    private ClientStatus clientStatus;

    public EventHubClient(String authEndpoint, String authToken) {
        try {
            producer = EventHubProducerClientFactory.create(authEndpoint, authToken);
        } catch (ConnectionRecoverableException e) {
            clientStatus = ClientStatus.RETRYING;
            log.error("Recoverable error occurred when creating Eventhub Client. Retry attempts will be made. Reason :"
                              + e.getMessage().replaceAll("[\r\n]", ""));
            log.debug("Recoverable error occurred when creating Eventhub Client using following attributes. Auth "
                              + "endpoint: " + authEndpoint.replaceAll("[\r\n]", "") + ". Retry attempts will be made. "
                              + "Reason : " + e.getMessage().replaceAll("[\r\n]", ""), e);
        } catch (ConnectionUnrecoverableException e) {
            clientStatus = ClientStatus.NOT_CONNECTED;
            log.error("Unrecoverable error occurred when creating Eventhub Client. Analytics event publishing will be"
                              + " disabled until issue is rectified. Reason: "
                              + e.getMessage().replaceAll("[\r\n]", ""));
            log.debug("Unrecoverable error occurred when creating Eventhub Client using following attributes. Auth "
                              + "endpoint: " + authEndpoint.replaceAll("[\r\n]", "") + ". Analytics event publishing "
                              + "will be disabled until issue is rectified. Reason: "
                              + e.getMessage().replaceAll("[\r\n]", ""), e);
            return;
        }
        clientStatus = ClientStatus.CONNECTED;
        batch = producer.createBatch();
        readWriteLock = new ReentrantReadWriteLock();
        sendSemaphore = new Semaphore(1);
    }

    public void sendEvent(String event) {
        if (producer == null) {
            log.error("EventHubClient has failed. Hence ignoring.");
            return;
        }
        EventData eventData = new EventData(event);
        readWriteLock.readLock().lock();
        try {
            boolean isAdded = batch.tryAdd(eventData);
            if (!isAdded) {
                try {
                    sendSemaphore.acquire();
                    isAdded = batch.tryAdd(eventData);
                    if (!isAdded) {
                        int size = batch.getCount();
                        producer.send(batch);
                        batch = producer.createBatch();
                        isAdded = batch.tryAdd(eventData);
                        log.debug("Published " + size + " events to Analytics cluster.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    sendSemaphore.release();
                }
            }

            if (isAdded) {
                log.debug("Adding event: " + event.replaceAll("[\r\n]", ""));
            } else {
                log.debug("Failed to add event: " + event.replaceAll("[\r\n]", ""));
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void flushEvents() {
        if (producer == null) {
            log.error("EventHubClient has failed. Hence ignoring.");
            return;
        }
        try {
            sendSemaphore.acquire();
            int size = batch.getCount();
            producer.send(batch);
            batch = producer.createBatch();
            log.debug("Flushed " + size + " events to Analytics cluster.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sendSemaphore.release();
        }
    }

    public ClientStatus getStatus() {
        return clientStatus;
    }
}
