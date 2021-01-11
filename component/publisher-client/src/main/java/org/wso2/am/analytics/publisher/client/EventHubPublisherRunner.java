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

import org.apache.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main class to send events to Azure Event Hub
 */
public class EventHubPublisherRunner {
    private static final Logger log = Logger.getLogger(EventHubPublisherRunner.class);

    public static void main(String[] args) {
        String concurrency = System.getenv("API_ANL_PUBLISHERS");
        int publisherCount;
        if (concurrency == null || concurrency.isEmpty()) {
            publisherCount = 1;
        } else {
            publisherCount = Integer.parseInt(concurrency);
        }
        String strEventCount = System.getenv("API_ANL_EVENT_COUNT");
        int eventCount;
        if (strEventCount == null || strEventCount.isEmpty()) {
            eventCount = 10;
        } else {
            eventCount = Integer.parseInt(strEventCount);
        }
        String target = System.getenv("API_ANL_SAS_TOKEN");
        if (target == null || target.isEmpty()) {
            log.error("SAS Token is not provided. Publisher can not be initialized");
        }
        log.info(("Publisher Started with following configs. publishers: " + publisherCount + ", Event "
                         + "count: " + eventCount).replaceAll("[\r\n]", ""));
        ExecutorService service = Executors.newFixedThreadPool(publisherCount);
        for (int i = 0; i < publisherCount; i++) {
            EventHubClient client = new EventHubClient(target, eventCount);
            try {
                service.submit(client);
            } catch (Exception e) {
                log.error(("Error occurred when submitting publisher job. " + e.getMessage()).replaceAll("[\r\n]", ""),
                          e);
            }
        }
        try {
            service.shutdown();
            if (!service.awaitTermination(10, TimeUnit.MINUTES)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
