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
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultResponseMetricEventBuilder extends AbstractMetricEventBuilder {
    private final List<String> requiredAttributes;
    private Map<String, Object> eventMap;

    protected DefaultResponseMetricEventBuilder() {
        requiredAttributes = ChoreoInputValidator.getInstance().getEventProperties(MetricSchema.RESPONSE);
        eventMap = new HashMap<>();
    }

    public DefaultResponseMetricEventBuilder setCorrelationId(String correlationId) {
        eventMap.put(Constants.CORRELATION_ID, correlationId);
        return this;
    }

    public DefaultResponseMetricEventBuilder setKeyType(String keyType) {
        eventMap.put(Constants.KEY_TYPE, keyType);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiId(String apiId) {
        eventMap.put(Constants.API_ID, apiId);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiName(String apiName) {
        eventMap.put(Constants.API_NAME, apiName);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiVersion(String apiVersion) {
        eventMap.put(Constants.API_VERSION, apiVersion);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiCreator(String apiCreator) {
        eventMap.put(Constants.API_CREATION, apiCreator);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiMethod(String apiMethod) {
        eventMap.put(Constants.API_METHOD, apiMethod);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiResourceTemplate(String apiResourceTemplate) {
        eventMap.put(Constants.API_RESOURCE_TEMPLATE, apiResourceTemplate);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApiCreatorTenantDomain(String apiCreatorTenantDomain) {
        eventMap.put(Constants.API_CREATOR_TENANT_DOMAIN, apiCreatorTenantDomain);
        return this;
    }

    public DefaultResponseMetricEventBuilder setDestination(String destination) {
        eventMap.put(Constants.DESTINATION, destination);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApplicationId(String applicationId) {
        eventMap.put(Constants.APPLICATION_ID, applicationId);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApplicationName(String applicationName) {
        eventMap.put(Constants.APPLICATION_NAME, applicationName);
        return this;
    }

    public DefaultResponseMetricEventBuilder setApplicationOwner(String applicationOwner) {
        eventMap.put(Constants.APPLICATION_OWNER, applicationOwner);
        return this;
    }

    public DefaultResponseMetricEventBuilder setRegionId(String regionId) {
        eventMap.put(Constants.REGION_ID, regionId);
        return this;
    }

    public DefaultResponseMetricEventBuilder setGatewayType(String gatewayType) {
        eventMap.put(Constants.GATEWAY_TYPE, gatewayType);
        return this;
    }

    public DefaultResponseMetricEventBuilder setUserAgent(String userAgent) {
        eventMap.put(Constants.USER_AGENT, userAgent);
        return this;
    }

    public DefaultResponseMetricEventBuilder setProxyResponseCode(int proxyResponseCode) {
        eventMap.put(Constants.PROXY_RESPONSE_CODE, proxyResponseCode);
        return this;
    }

    public DefaultResponseMetricEventBuilder setTargetResponseCode(int targetResponseCode) {
        eventMap.put(Constants.TARGET_RESPONSE_CODE, targetResponseCode);
        return this;
    }

    public DefaultResponseMetricEventBuilder setResponseCacheHit(boolean responseCacheHit) {
        eventMap.put(Constants.RESPONSE_CACHE_HIT, responseCacheHit);
        return this;
    }

    public DefaultResponseMetricEventBuilder setResponseLatency(int responseLatency) {
        eventMap.put(Constants.RESPONSE_LATENCY, responseLatency);
        return this;
    }

    public DefaultResponseMetricEventBuilder setBackendLatency(int backendLatency) {
        eventMap.put(Constants.BACKEND_LATENCY, backendLatency);
        return this;
    }

    public DefaultResponseMetricEventBuilder setRequestMediationLatency(int requestMediationLatency) {
        eventMap.put(Constants.REQUEST_MEDIATION_LATENCY, requestMediationLatency);
        return this;
    }

    public DefaultResponseMetricEventBuilder setResponseMediationLatency(int responseMediationLatency) {
        eventMap.put(Constants.RESPONSE_MEDIATION_LATENCY, responseMediationLatency);
        return this;
    }

    public DefaultResponseMetricEventBuilder setDeploymentId(String deploymentId) {
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
    protected boolean isKeyPresent(String key) {
        return requiredAttributes.contains(key);
    }

    @Override
    protected Map<String, Object> buildEvent() {
        return eventMap;
    }

    @Override protected MetricEventBuilder addVerifiedAttribute(String key, Object value) {
        eventMap.put(key, value);
        return this;
    }
}
