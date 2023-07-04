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
package org.wso2.am.analytics.publisher.reporter.moesif;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;

import java.util.TimerTask;

/**
 * Responsible for periodically calling the Moesif microservice and
 * refreshing the internal map.
 */
public class MissedEventHandler extends TimerTask {
    private static final Logger log = LoggerFactory.getLogger(MissedEventHandler.class);
    private final MoesifKeyRetriever keyRetriever;

    public MissedEventHandler(MoesifKeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
    }

    @Override
    public void run() {
        // clear the internal map of orgID-MoesifKey
        keyRetriever.clearMoesifKeyMap();
        // clear the environment map
        keyRetriever.clearEnvMap();
        // refresh the internal map of orgID-MoesifKey
        keyRetriever.initOrRefreshOrgIDMoesifKeyMap();
    }
}
