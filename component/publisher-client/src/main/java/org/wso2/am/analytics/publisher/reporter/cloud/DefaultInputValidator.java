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

import org.wso2.am.analytics.publisher.properties.Properties;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.wso2.am.analytics.publisher.util.Constants.API_CONTEXT;
import static org.wso2.am.analytics.publisher.util.Constants.API_CREATION;
import static org.wso2.am.analytics.publisher.util.Constants.API_CREATOR_TENANT_DOMAIN;
import static org.wso2.am.analytics.publisher.util.Constants.API_ID;
import static org.wso2.am.analytics.publisher.util.Constants.API_METHOD;
import static org.wso2.am.analytics.publisher.util.Constants.API_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.API_RESOURCE_TEMPLATE;
import static org.wso2.am.analytics.publisher.util.Constants.API_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.API_VERSION;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_OWNER;
import static org.wso2.am.analytics.publisher.util.Constants.BACKEND_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.CORRELATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.DESTINATION;
import static org.wso2.am.analytics.publisher.util.Constants.ENVIRONMENT_ID;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_MESSAGE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.GATEWAY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.KEY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.ORGANIZATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.PROPERTIES;
import static org.wso2.am.analytics.publisher.util.Constants.PROXY_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.REGION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_TIMESTAMP;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_CACHE_HIT;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.TARGET_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.USER_AGENT_HEADER;
import static org.wso2.am.analytics.publisher.util.Constants.USER_IP;

/**
 * Input Validator for {@link DefaultAnalyticsMetricReporter}. Validator holds all required attributes against which
 * inputs will be validated.
 */
public class DefaultInputValidator {
    private static final DefaultInputValidator INSTANCE = new DefaultInputValidator();
    private static final Map<String, Class> responseSchema = Stream.of(
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
            new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_METHOD, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CONTEXT, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_RESOURCE_TEMPLATE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
            new AbstractMap.SimpleImmutableEntry<>(DESTINATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(USER_AGENT_HEADER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_CACHE_HIT, Boolean.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(BACKEND_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_MEDIATION_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_MEDIATION_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(USER_IP, String.class),
            new AbstractMap.SimpleImmutableEntry<>(PROPERTIES, Properties.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Class> faultSchema = Stream.of(
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
            new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_MESSAGE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(PROPERTIES, Properties.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Class> choreoResponseSchema = Stream.of(
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
            new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_METHOD, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_RESOURCE_TEMPLATE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CONTEXT, String.class),
            new AbstractMap.SimpleImmutableEntry<>(DESTINATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ORGANIZATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ENVIRONMENT_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(USER_AGENT_HEADER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_CACHE_HIT, Boolean.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(BACKEND_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_MEDIATION_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(RESPONSE_MEDIATION_LATENCY, Long.class),
            new AbstractMap.SimpleImmutableEntry<>(USER_IP, String.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Class> choreoFaultSchema = Stream.of(
            new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
            new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(ERROR_MESSAGE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
            new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
            new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
            new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ORGANIZATION_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(ENVIRONMENT_ID, String.class),
            new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
            new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
            new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final List<String> configProperties = new ArrayList<>();


    private DefaultInputValidator() {
        //private constructor
    }

    public static DefaultInputValidator getInstance() {
        return INSTANCE;
    }

    public Map<String, Class> getEventProperties(MetricSchema schema) {
        switch (schema) {
            case RESPONSE:
                return responseSchema;
            case ERROR:
                return faultSchema;
            case CHOREO_RESPONSE:
                return choreoResponseSchema;
            case CHOREO_ERROR:
                return choreoFaultSchema;
            default:
                return new HashMap<>();
        }
    }

    public List<String> getConfigProperties() {
        return configProperties;
    }
}
