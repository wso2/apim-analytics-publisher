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
package org.wso2.am.analytics.publisher.reporter.choreo;

import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.client.EventHubClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Will removes the events from queues and send then to the endpoints.
 */
public class QueueWorker implements Runnable {

    private static final Logger log = Logger.getLogger(QueueWorker.class);
    private BlockingQueue<String> eventQueue;
    private ExecutorService executorService;
    private EventHubClient client;

    public QueueWorker(BlockingQueue<String> queue, EventHubClient client, ExecutorService executorService) {
        this.client = client;
        this.eventQueue = queue;
        this.executorService = executorService;
    }

    public void run() {
        try {
            if (log.isDebugEnabled()) {
                log.debug(eventQueue.size() + " messages in queue before " +
                                  Thread.currentThread().getName().replaceAll("[\r\n]", "")
                                  + " worker has polled queue");
            }
            ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) executorService);
            do {
                String event = eventQueue.poll();
                if (event != null) {
                    client.sendEvent(event);
                } else {
                    //if we are done with consuming blocking queue flush the EventHub Client batch
                    client.flushEvents();
                    break;
                }
                if (eventQueue.size() == 0) {
                    //if we are done with consuming blocking queue flush the EventHub Client batch
                    client.flushEvents();
                    break;
                }
            } while (threadPoolExecutor.getActiveCount() == 1 && eventQueue.size() != 0);
            //while condition to handle possible task rejections
            if (log.isDebugEnabled()) {
                log.debug(eventQueue.size() + " messages in queue after " +
                                  Thread.currentThread().getName().replaceAll("[\r\n]", "")
                                  + " worker has finished work");
            }
        } catch (Throwable e) {
            log.error("Error in passing events to Event Hub client. Events dropped", e);
        }
    }
}
