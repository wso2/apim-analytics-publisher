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

import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.AbstractMetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.HashMap;
import java.util.Map;

public class ChoreoResponseMetricEventBuilder extends AbstractMetricEventBuilder {
    private final String[] requiredAttributes;
    private Map<String, Object> eventMap;

    protected ChoreoResponseMetricEventBuilder() {
        requiredAttributes = ChoreoInputValidator.getInstance().getEventSchema(MetricSchema.RESPONSE);
        eventMap = new HashMap<>();
    }

    public ChoreoResponseMetricEventBuilder setCorrelationId(String correlationId) {
        eventMap.put(Constants.CORRELATION_ID, correlationId);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setKeyType(String keyType) {
        eventMap.put(Constants.KEY_TYPE, keyType);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiId(String apiId) {
        eventMap.put(Constants.API_ID, apiId);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiName(String apiName) {
        eventMap.put(Constants.API_NAME, apiName);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiVersion(String apiVersion) {
        eventMap.put(Constants.API_VERSION, apiVersion);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiCreator(String apiCreator) {
        eventMap.put(Constants.API_CREATION, apiCreator);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiMethod(String apiMethod) {
        eventMap.put(Constants.API_METHOD, apiMethod);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiResourceTemplate(String apiResourceTemplate) {
        eventMap.put(Constants.API_RESOURCE_TEMPLATE, apiResourceTemplate);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApiCreatorTenantDomain(String apiCreatorTenantDomain) {
        eventMap.put(Constants.API_CREATOR_TENANT_DOMAIN, apiCreatorTenantDomain);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setDestination(String destination) {
        eventMap.put(Constants.DESTINATION, destination);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApplicationId(String applicationId) {
        eventMap.put(Constants.APPLICATION_ID, applicationId);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApplicationName(String applicationName) {
        eventMap.put(Constants.APPLICATION_NAME, applicationName);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setApplicationOwner(String applicationOwner) {
        eventMap.put(Constants.APPLICATION_OWNER, applicationOwner);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setRegionId(String regionId) {
        eventMap.put(Constants.REGION_ID, regionId);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setGatewayType(String gatewayType) {
        eventMap.put(Constants.GATEWAY_TYPE, gatewayType);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setUserAgent(String userAgent) {
        eventMap.put(Constants.USER_AGENT, userAgent);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setProxyResponseCode(int proxyResponseCode) {
        eventMap.put(Constants.PROXY_RESPONSE_CODE, proxyResponseCode);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setTargetResponseCode(int targetResponseCode) {
        eventMap.put(Constants.TARGET_RESPONSE_CODE, targetResponseCode);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setResponseCacheHit(boolean responseCacheHit) {
        eventMap.put(Constants.RESPONSE_CACHE_HIT, responseCacheHit);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setResponseLatency(int responseLatency) {
        eventMap.put(Constants.RESPONSE_LATENCY, responseLatency);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setBackendLatency(int backendLatency) {
        eventMap.put(Constants.BACKEND_LATENCY, backendLatency);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setRequestMediationLatency(int requestMediationLatency) {
        eventMap.put(Constants.REQUEST_MEDIATION_LATENCY, requestMediationLatency);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setResponseMediationLatency(int responseMediationLatency) {
        eventMap.put(Constants.RESPONSE_MEDIATION_LATENCY, responseMediationLatency);
        return this;
    }

    public ChoreoResponseMetricEventBuilder setDeploymentId(String deploymentId) {
        eventMap.put(Constants.DEPLOYMENT_ID, deploymentId);
        return this;
    }

    @Override
    public boolean validate() throws MetricReportingException {
        for (String attributeKey : requiredAttributes) {
            Object attribute = eventMap.get(attributeKey);
            if (attribute == null) {
                throw new MetricReportingException(attributeKey + " is missing in metric data");
            } else if (attribute instanceof String) {
                if (((String) attribute).isEmpty()) {
                    throw new MetricReportingException(attributeKey + " is missing in metric data");
                }
            }
        }
        return true;
    }

    @Override
    protected Map<String, Object> buildEvent() {
        return eventMap;
    }
}
