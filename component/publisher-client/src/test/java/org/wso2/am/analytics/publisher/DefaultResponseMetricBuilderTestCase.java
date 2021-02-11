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

package org.wso2.am.analytics.publisher;

import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.reporter.cloud.DefaultCounterMetric;
import org.wso2.am.analytics.publisher.reporter.cloud.EventQueue;
import org.wso2.am.analytics.publisher.util.Constants;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

public class DefaultResponseMetricBuilderTestCase {
    private static final Logger log = Logger.getLogger(DefaultResponseMetricBuilderTestCase.class);

    @Test(expectedExceptions = MetricReportingException.class)
    public void testMissingAttributes() throws MetricCreationException, MetricReportingException {
        EventQueue queue = new EventQueue(100, 1, null);
        DefaultCounterMetric metric = new DefaultCounterMetric("test.metric", queue, MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        builder.addAttribute("apiName", "PizzaShack");
        builder.build();
    }

    @Test(expectedExceptions = MetricReportingException.class)
    public void testAttributesWithInvalidTypes() throws MetricCreationException, MetricReportingException {
        EventQueue queue = new EventQueue(100, 1, null);
        DefaultCounterMetric metric = new DefaultCounterMetric("test.metric", queue, MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        builder.addAttribute(Constants.REQUEST_TIMESTAMP, System.currentTimeMillis())
                .addAttribute(Constants.CORRELATION_ID, "1234-4567")
                .addAttribute(Constants.KEY_TYPE, "prod")
                .addAttribute(Constants.API_ID, "9876-54f1")
                .addAttribute(Constants.API_NAME, "PizzaShack")
                .addAttribute(Constants.API_VERSION, "1.0.0")
                .addAttribute(Constants.API_CREATION, "admin")
                .addAttribute(Constants.API_METHOD, "POST")
                .addAttribute(Constants.API_METHOD, "POST")
                .addAttribute(Constants.API_RESOURCE_TEMPLATE, "/resource/{value}")
                .addAttribute(Constants.API_CREATOR_TENANT_DOMAIN, "carbon.super")
                .addAttribute(Constants.DESTINATION, "localhost:8080")
                .addAttribute(Constants.APPLICATION_ID, "3445-6778")
                .addAttribute(Constants.APPLICATION_NAME, "default")
                .addAttribute(Constants.APPLICATION_OWNER, "admin")
                .addAttribute(Constants.REGION_ID, "NA")
                .addAttribute(Constants.GATEWAY_TYPE, "Synapse")
                .addAttribute(Constants.USER_AGENT, "Mozilla")
                .addAttribute(Constants.PROXY_RESPONSE_CODE, 401)
                .addAttribute(Constants.TARGET_RESPONSE_CODE, "someString")
                .addAttribute(Constants.RESPONSE_CACHE_HIT, true)
                .addAttribute(Constants.RESPONSE_LATENCY, 2000)
                .addAttribute(Constants.BACKEND_LATENCY, 3000)
                .addAttribute(Constants.REQUEST_MEDIATION_LATENCY, "1000")
                .addAttribute(Constants.RESPONSE_MEDIATION_LATENCY, 1000)
                .addAttribute(Constants.DEPLOYMENT_ID, "prod")
                .build();
    }

    @Test
    public void testMetricBuilder() throws MetricCreationException, MetricReportingException {
        EventQueue queue = new EventQueue(100, 1, null);
        DefaultCounterMetric metric = new DefaultCounterMetric("test.metric", queue, MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        String uaString = "Mozilla/5.0 (iPhone; CPU iPhone OS 5_1_1 like Mac OS X) AppleWebKit/534.46 (KHTML, "
                + "like Gecko) Version/5.1 Mobile/9B206 Safari/7534.48.3";

        Map<String, Object> eventMap = builder
                .addAttribute(Constants.REQUEST_TIMESTAMP, OffsetDateTime.now(Clock.systemUTC()).toString())
                .addAttribute(Constants.CORRELATION_ID, "1234-4567")
                .addAttribute(Constants.KEY_TYPE, "prod")
                .addAttribute(Constants.API_ID, "9876-54f1")
                .addAttribute(Constants.API_TYPE, "HTTP")
                .addAttribute(Constants.API_NAME, "PizzaShack")
                .addAttribute(Constants.API_VERSION, "1.0.0")
                .addAttribute(Constants.API_CREATION, "admin")
                .addAttribute(Constants.API_METHOD, "POST")
                .addAttribute(Constants.API_RESOURCE_TEMPLATE, "/resource/{value}")
                .addAttribute(Constants.API_CREATOR_TENANT_DOMAIN, "carbon.super")
                .addAttribute(Constants.DESTINATION, "localhost:8080")
                .addAttribute(Constants.APPLICATION_ID, "3445-6778")
                .addAttribute(Constants.APPLICATION_NAME, "default")
                .addAttribute(Constants.APPLICATION_OWNER, "admin")
                .addAttribute(Constants.REGION_ID, "NA")
                .addAttribute(Constants.GATEWAY_TYPE, "Synapse")
                .addAttribute(Constants.USER_AGENT_HEADER, uaString)
                .addAttribute(Constants.PROXY_RESPONSE_CODE, 401)
                .addAttribute(Constants.TARGET_RESPONSE_CODE, 401)
                .addAttribute(Constants.RESPONSE_CACHE_HIT, true)
                .addAttribute(Constants.RESPONSE_LATENCY, 2000L)
                .addAttribute(Constants.BACKEND_LATENCY, 3000L)
                .addAttribute(Constants.REQUEST_MEDIATION_LATENCY, 1000L)
                .addAttribute(Constants.RESPONSE_MEDIATION_LATENCY, 1000L)
                .addAttribute(Constants.DEPLOYMENT_ID, "prod")
                .build();

        Assert.assertFalse(eventMap.isEmpty());
        Assert.assertEquals(eventMap.size(), 28, "Some attributes are missing from the resulting event map");
        Assert.assertEquals(eventMap.get(Constants.EVENT_TYPE), "response", "Event type should be set to fault");
        Assert.assertEquals(eventMap.get(Constants.USER_AGENT), "Mobile Safari",
                "User agent should be set to Mobile Safari");
        Assert.assertEquals(eventMap.get(Constants.PLATFORM), "iOS", "Platform should be set to iOS");
        Assert.assertEquals(eventMap.get(Constants.API_TYPE), "HTTP", "API type should be set to HTTP");
    }
}
