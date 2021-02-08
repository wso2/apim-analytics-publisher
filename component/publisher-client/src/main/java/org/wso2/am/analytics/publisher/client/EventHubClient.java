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

    public EventHubClient(String authEndpoint, String authToken) {
        producer = EventHubProducerClientFactory.create(authEndpoint, authToken);
        if(producer == null) {
            log.error("EventHubClient initialization failed.");
            return;
        }
        batch = producer.createBatch();
        readWriteLock = new ReentrantReadWriteLock();
        sendSemaphore = new Semaphore(1);
    }

    public void sendEvent(String event) {
        if(producer == null) {
            log.error("EventHubClient has failed. Hence ignoring.");
            return;
        }
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
                        log.debug("Published " + size + " events to Analytics cluster.");
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
        if(producer == null) {
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
}
