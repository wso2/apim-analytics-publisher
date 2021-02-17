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

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.ConnectionRecoverableException;
import org.wso2.am.analytics.publisher.exception.ConnectionUnrecoverableException;
import org.wso2.am.analytics.publisher.reporter.cloud.DefaultAnalyticsThreadFactory;
import org.wso2.am.analytics.publisher.util.BackoffRetryCounter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Event Hub client is responsible for sending events to
 * Azure Event Hub.
 */
public class EventHubClient {
    private static final Logger log = Logger.getLogger(EventHubClient.class);
    private final String authEndpoint;
    private final String authToken;
    private final ReadWriteLock readWriteLock;
    private final BackoffRetryCounter producerRetryCounter;
    private final BackoffRetryCounter eventBatchRetryCounter;
    private final Lock threadBarrier;
    private final AmqpRetryOptions retryOptions;
    private Condition waitCondition;
    private EventHubProducerClient producer;
    private EventDataBatch batch;
    private ClientStatus clientStatus;
    private ScheduledExecutorService scheduledExecutorService;

    public EventHubClient(String authEndpoint, String authToken, AmqpRetryOptions retryOptions) {
        threadBarrier = new ReentrantLock();
        waitCondition = threadBarrier.newCondition();
        readWriteLock = new ReentrantReadWriteLock(true);
        scheduledExecutorService = Executors.newScheduledThreadPool(2, new DefaultAnalyticsThreadFactory(
                "Reconnection-Service"));
        producerRetryCounter = new BackoffRetryCounter();
        eventBatchRetryCounter = new BackoffRetryCounter();
        this.authEndpoint = authEndpoint;
        this.authToken = authToken;
        this.retryOptions = retryOptions;
        createProducerWithRetry(authEndpoint, authToken, retryOptions, true);
    }

    private void retryWithBackoff(String authEndpoint, String authToken,
                                  AmqpRetryOptions retryOptions, boolean createBatch) {
        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                createProducerWithRetry(authEndpoint, authToken, retryOptions, createBatch);
            }
        }, producerRetryCounter.getTimeIntervalMillis(), TimeUnit.MILLISECONDS);
        producerRetryCounter.increment();
    }

    private void createProducerWithRetry(String authEndpoint, String authToken,
                                         AmqpRetryOptions retryOptions, boolean createBatch) {
        try {
            if (producer != null) {
                producer.close();
            }
            producer = EventHubProducerClientFactory.create(authEndpoint, authToken, retryOptions);
            try {
                if (createBatch) {
                    batch = producer.createBatch();
                }
            } catch (IllegalStateException e) {
                throw new ConnectionRecoverableException("Event batch creation failed. " + e.getMessage()
                        .replaceAll("[\r\n]", ""));
            }
            clientStatus = ClientStatus.CONNECTED;
            producerRetryCounter.reset();
            try {
                threadBarrier.lock();
                waitCondition.signalAll();
            } finally {
                threadBarrier.unlock();
            }
        } catch (ConnectionRecoverableException e) {
            clientStatus = ClientStatus.RETRYING;
            log.error("Recoverable error occurred when creating Eventhub Client. Retry attempts will be made. Reason :"
                              + e.getMessage().replaceAll("[\r\n]", ""));
            log.debug("Recoverable error occurred when creating Eventhub Client using following attributes. Auth "
                              + "endpoint: " + authEndpoint.replaceAll("[\r\n]", "") + ". Retry attempts will be made. "
                              + "Reason : " + e.getMessage().replaceAll("[\r\n]", ""), e);
            retryWithBackoff(authEndpoint, authToken, retryOptions, createBatch);
        } catch (ConnectionUnrecoverableException e) {
            clientStatus = ClientStatus.NOT_CONNECTED;
            log.error("Unrecoverable error occurred when creating Eventhub Client. Analytics event publishing will be"
                              + " disabled until issue is rectified. Reason: "
                              + e.getMessage().replaceAll("[\r\n]", ""));
            log.debug("Unrecoverable error occurred when creating Eventhub Client using following attributes. Auth "
                              + "endpoint: " + authEndpoint.replaceAll("[\r\n]", "") + ". Analytics event publishing "
                              + "will be disabled until issue is rectified. Reason: "
                              + e.getMessage().replaceAll("[\r\n]", ""), e);
        }
    }

    public void sendEvent(String event) {
        if (clientStatus == ClientStatus.CONNECTED) {
            EventData eventData = new EventData(event);
            readWriteLock.readLock().lock();
            try {
                boolean isAdded = batch.tryAdd(eventData);
                if (!isAdded) {
                    try {
                        readWriteLock.readLock().unlock();
                        readWriteLock.writeLock().lock();
                        isAdded = batch.tryAdd(eventData);
                        if (!isAdded) {
                            int size = batch.getCount();
                            producer.send(batch);
                            batch = createBatchWithRetry();
                            isAdded = batch.tryAdd(eventData);
                            log.info("Published " + size + " events to Analytics cluster.");
                        }
                    } catch (Exception e) {
                        if (e.getCause() instanceof TimeoutException) {
                            log.error("Timeout occurred after retrying " + retryOptions.getMaxRetries() + " "
                                              + "times with an timeout of " + retryOptions.getTryTimeout() + " "
                                              + "seconds while trying to publish EventDataBatch. Next retry cycle "
                                              + "will begin shortly.");
                            readWriteLock.writeLock().unlock();
                            sendEvent(event);
                        } else if (e instanceof AmqpException && isAuthenticationFailure((AmqpException) e)) {
                            //if authentication error try to reinitialize publisher. Retrying will deal with any
                            // network or revocation failures.
                            log.error("Authentication issue happened. Producer client will be re-initialized "
                                              + "retaining the EventDataBatch");
                            this.clientStatus = ClientStatus.RETRYING;
                            createProducerWithRetry(authEndpoint, authToken, retryOptions, false);
                            readWriteLock.writeLock().unlock();
                            sendEvent(event);
                        } else if (e instanceof AmqpException && ((AmqpException) e).getErrorCondition()
                                == AmqpErrorCondition.RESOURCE_LIMIT_EXCEEDED) {
                            //If resource limit is exceeded we will retry after a constant delay
                            log.error("Resource limit exceeded when publishing EventDataBatch. Operation will be "
                                              + "retried after constant delay");
                            try {
                                Thread.sleep(1000 * 60);
                            } catch (InterruptedException interruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            readWriteLock.writeLock().unlock();
                            sendEvent(event);
                        } else {
                            //For any other exception
                            log.error("Unknown error occurred while publishing EventDataBatch. Producer client will "
                                              + "be re-initialized. Events may be lost in the process.");
                            this.clientStatus = ClientStatus.RETRYING;
                            readWriteLock.writeLock().unlock();
                            createProducerWithRetry(authEndpoint, authToken, retryOptions, true);
                        }
                    } finally {
                        try {
                            readWriteLock.writeLock().unlock();
                        } catch (IllegalMonitorStateException e) {
                            //ignore
                        }
                    }
                }
                if (isAdded) {
                    log.debug("Adding event: " + event.replaceAll("[\r\n]", ""));
                } else {
                    log.debug("Failed to add event: " + event.replaceAll("[\r\n]", ""));
                }
            } finally {
                try {
                    readWriteLock.readLock().unlock();
                } catch (IllegalMonitorStateException e) {
                    //ignore
                }
            }
        } else {
            try {
                threadBarrier.lock();
                log.debug(Thread.currentThread().getName().replaceAll("[\r\n]", "") + " will be parked as EventHub "
                                  + "Client is inactive.");
                waitCondition.await();
                log.debug(Thread.currentThread().getName().replaceAll("[\r\n]", "") + " will be resumes as EventHub "
                                  + "Client is active.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                threadBarrier.unlock();
            }
            sendEvent(event);
        }
    }

    private boolean isAuthenticationFailure(AmqpException exception) {
        AmqpErrorCondition condition = exception.getErrorCondition();
        return (condition == AmqpErrorCondition.UNAUTHORIZED_ACCESS ||
                condition == AmqpErrorCondition.PUBLISHER_REVOKED_ERROR);
    }

    private EventDataBatch createBatchWithRetry() {
        try {
            EventDataBatch batch = producer.createBatch();
            eventBatchRetryCounter.reset();
            return batch;
        } catch (IllegalStateException e) {
            log.error("Error in creating EventDataBatch. Will be retried in "
                              + eventBatchRetryCounter.getTimeInterval().replaceAll("[\r\n]", ""));
            try {
                Thread.sleep(eventBatchRetryCounter.getTimeIntervalMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            eventBatchRetryCounter.increment();
            return createBatchWithRetry();
        }
    }

    public void flushEvents() {
        try {
            readWriteLock.writeLock().lock();
            int size = batch.getCount();
            producer.send(batch);
            batch = producer.createBatch();
            log.debug("Flushed " + size + " events to Analytics cluster.");
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public ClientStatus getStatus() {
        return clientStatus;
    }


}
