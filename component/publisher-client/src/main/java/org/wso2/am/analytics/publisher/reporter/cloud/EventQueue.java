/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.reporter.cloud;

import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.client.EventHubClient;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bounded concurrent queue wrapping {@link java.util.concurrent.ArrayBlockingQueue}
 */
public class EventQueue {

    private static final Logger log = Logger.getLogger(EventQueue.class);
    private BlockingQueue<MetricEventBuilder> eventQueue;
    private ExecutorService executorService;
    private EventHubClient client;

    public EventQueue(int queueSize, int workerThreadCount, EventHubClient client) {
        this.client = client;
        // Note : Using a fixed worker thread pool and a bounded queue to control the load on the server
        executorService = Executors.newFixedThreadPool(workerThreadCount,
                                                       new DefaultAnalyticsThreadFactory("Queue-Worker"));
        eventQueue = new ArrayBlockingQueue<>(queueSize);
    }

    public void put(MetricEventBuilder builder) {
        try {
            eventQueue.put(builder);
            executorService.submit(new QueueWorker(eventQueue, client, executorService));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RejectedExecutionException e) {
            log.warn("Task submission failed. Task queue might be full", e);
        }

    }

    public boolean isQueueEmpty() {
        return eventQueue.peek() == null;
    }

    @Override
    protected void finalize() throws Throwable {
        executorService.shutdown();
        super.finalize();
    }

    protected EventHubClient getClient() {
        return this.client;
    }
}
