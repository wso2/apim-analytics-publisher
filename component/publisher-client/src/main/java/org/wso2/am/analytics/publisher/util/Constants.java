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

package org.wso2.am.analytics.publisher.util;
/**
 * Class to hold String constants
 */
public class Constants {
    public static final String CHOREO_ANALYTICS_REPORTER_FQCN = "org.wso2.am.analytics.publisher.reporter"
            + ".ChoreoAnalyticsMetricReporter";
    public static final String CORRELATION_ID = "correlationId";
    public static final String KEY_TYPE = "keyType";
    public static final String API_ID = "apiId";
    public static final String API_NAME = "apiName";
    public static final String API_CONTEXT = "apiContext";
    public static final String API_VERSION = "apiVersion";
    public static final String API_CREATION = "apiCreator";
    public static final String API_METHOD = "apiMethod";
    public static final String API_RESOURCE_TEMPLATE = "apiResourceTemplate";
    public static final String API_CREATOR_TENANT_DOMAIN = "apiCreatorTenantDomain";
    public static final String DESTINATION = "destination";
    public static final String APPLICATION_ID = "applicationId";
    public static final String APPLICATION_NAME = "applicationName";
    public static final String APPLICATION_CONSUMER_KEY = "applicationConsumerKey";
    public static final String APPLICATION_OWNER = "applicationOwner";
    public static final String REGION_ID = "regionId";
    public static final String GATEWAY_TYPE = "gatewayType";
    public static final String USER_AGENT = "userAgent";
    public static final String PROXY_RESPONSE_CODE = "proxyResponseCode";
    public static final String TARGET_RESPONSE_CODE = "targetResponseCode";
    public static final String RESPONSE_CACHE_HIT = "responseCacheHit";
    public static final String RESPONSE_LATENCY = "responseLatency";
    public static final String BACKEND_LATENCY = "backendLatency";
    public static final String REQUEST_MEDIATION_LATENCY = "requestMediationLatency";
    public static final String RESPONSE_MEDIATION_LATENCY = "responseMediationLatency";
    public static final String DEPLOYMENT_ID = "deploymentId";
    public static final String REQUEST_TIMESTAMP = "requestTimestamp";
    public static final String EVENT_TYPE = "eventType";

    public static final String ERROR_TYPE = "errorType";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_MESSAGE = "errorMessage";

    public static final String AUTH_API_URL = "auth.api.url";
    public static final String AUTH_API_TOKEN = "auth.api.token";
    public static final String TOKEN_API_URL = "token.api.url";
    public static final String CONSUMER_KEY = "consumer.key";
    public static final String CONSUMER_SECRET = "consumer.secret";
    public static final String SAS_TOKEN = "sas.token";
    public static final String DEFAULT_REPORTER = "default";

    public static final String AUTH_TOKEN_ENV_VAR = "API_ANL_AUTH_TOKEN";
}
