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

import org.wso2.am.analytics.publisher.reporter.MetricSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.wso2.am.analytics.publisher.util.Constants.API_CREATION;
import static org.wso2.am.analytics.publisher.util.Constants.API_CREATOR_TENANT_DOMAIN;
import static org.wso2.am.analytics.publisher.util.Constants.API_ID;
import static org.wso2.am.analytics.publisher.util.Constants.API_METHOD;
import static org.wso2.am.analytics.publisher.util.Constants.API_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.API_RESOURCE_TEMPLATE;
import static org.wso2.am.analytics.publisher.util.Constants.API_VERSION;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_OWNER;
import static org.wso2.am.analytics.publisher.util.Constants.BACKEND_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.CORRELATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.DEPLOYMENT_ID;
import static org.wso2.am.analytics.publisher.util.Constants.DESTINATION;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_MESSAGE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.EVENT_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.GATEWAY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.KEY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.PROXY_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.REGION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_TIMESTAMP;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_CACHE_HIT;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.TARGET_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.USER_AGENT;

/**
 * Input Validator for {@link ChoreoAnalyticsMetricReporter}. Validator holds all required attributes against which
 * inputs will be validated.
 */
public class ChoreoInputValidator {
    private static final ChoreoInputValidator INSTANCE = new ChoreoInputValidator();
    private static final List<String> responseSchema = Stream.of(CORRELATION_ID, KEY_TYPE, API_ID, API_NAME,
                                                                 API_VERSION, API_CREATION, API_METHOD,
                                                                 API_RESOURCE_TEMPLATE,
                                                                 API_CREATOR_TENANT_DOMAIN, DESTINATION, APPLICATION_ID,
                                                                 APPLICATION_NAME, APPLICATION_OWNER,
                                                                 REGION_ID, GATEWAY_TYPE, USER_AGENT,
                                                                 PROXY_RESPONSE_CODE,
                                                                 TARGET_RESPONSE_CODE, RESPONSE_CACHE_HIT,
                                                                 RESPONSE_LATENCY,
                                                                 BACKEND_LATENCY, REQUEST_MEDIATION_LATENCY,
                                                                 RESPONSE_MEDIATION_LATENCY, DEPLOYMENT_ID).collect(
            Collectors.toList());
    private static final List<String> faultSchema = Stream.of(REQUEST_TIMESTAMP, CORRELATION_ID, KEY_TYPE, ERROR_TYPE,
                                                              ERROR_CODE, ERROR_MESSAGE, API_ID, API_NAME, API_VERSION,
                                                              API_CREATION, API_CREATOR_TENANT_DOMAIN, APPLICATION_ID,
                                                              APPLICATION_NAME, APPLICATION_OWNER, REGION_ID,
                                                              GATEWAY_TYPE, PROXY_RESPONSE_CODE, TARGET_RESPONSE_CODE,
                                                              DEPLOYMENT_ID, EVENT_TYPE).collect(Collectors.toList());
    private static final List<String> configProperties = new ArrayList<>();


    private ChoreoInputValidator() {
        //private constructor
    }

    public static ChoreoInputValidator getInstance() {
        return INSTANCE;
    }

    public List<String> getEventProperties(MetricSchema schema) {
        if (MetricSchema.RESPONSE == schema) {
            return responseSchema;
        } else if (MetricSchema.ERROR == schema) {
            return faultSchema;
        } else {
            return new ArrayList<>();
        }
    }

    public List<String> getConfigProperties() {
        return configProperties;
    }
}
