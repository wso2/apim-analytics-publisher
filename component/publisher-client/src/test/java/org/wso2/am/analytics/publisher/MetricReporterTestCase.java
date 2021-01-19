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
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricReporter;
import org.wso2.am.analytics.publisher.reporter.MetricReporterFactory;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;

import java.util.HashMap;
import java.util.Map;

public class MetricReporterTestCase {
    private static final Logger log = Logger.getLogger(MetricReporterTestCase.class);


    @Test(expectedExceptions = MetricCreationException.class)
    public void testMetricReporterCreationWithoutConfigs() throws MetricCreationException, MetricReportingException {
        try {
            createAndPublish(null, null);
        } catch (MetricCreationException e) {
            log.error(e);
            throw e;
        }
    }

    @Test(expectedExceptions = MetricCreationException.class, dependsOnMethods =
            {"testMetricReporterCreationWithoutConfigs"})
    public void testMetricReporterCreationWithMissingConfigs() throws MetricCreationException,
                                                                      MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        createAndPublish(configs, null);
    }

    @Test(expectedExceptions = MetricCreationException.class, dependsOnMethods =
            {"testMetricReporterCreationWithMissingConfigs"})
    public void testMetricReporterCreationWithNullConfigs() throws MetricCreationException,
                                                                   MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", null);
        createAndPublish(configs, null);
    }

    @Test(expectedExceptions = MetricCreationException.class, dependsOnMethods =
            {"testMetricReporterCreationWithNullConfigs"})
    public void testMetricReporterCreationWithEmptyConfigs() throws MetricCreationException,
                                                                    MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "");
        createAndPublish(configs, null);
    }

    @Test(dependsOnMethods = {"testMetricReporterCreationWithEmptyConfigs"})
    public void testMetricReporterCreation() {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "some_key");

        Map<String, String> event = new HashMap<>();
        event.put("correlationId", "12345");
        event.put("keyType", "pub_key");
        event.put("apiId", "121");
        event.put("apiName", "pizzashak");
        event.put("apiContext", "/pizzashak");
        event.put("apiVersion", "1.0.0");
        event.put("responseCode", "200");
        event.put("apiCreator", "admin");
        event.put("apiMethod", "GET");
        event.put("apiCreatorTenantDomain", "carbon.super");
        event.put("destination", "localhost");
        event.put("applicationId", "5");
        event.put("applicationName", "pizza-app");
        event.put("applicationConsumerKey", "consumer_key");
        event.put("applicationOwner", "admin");
        event.put("regionId", "2");
        event.put("gatewayType", "synapse");
        event.put("userAgent", "Mozilla");
        event.put("responseCacheHit", "false");
        event.put("responseLatency", "2000");
        event.put("requestMediationLatency", "250");
        event.put("responseMediationLatency", "1750");
        event.put("deploymentId", "1");
        try {
            createAndPublish(configs, event);
        } catch (MetricReportingException e) {
            log.error(e.getMessage(), e);
            Assert.fail("Metric reporting failed failed");
        } catch (MetricCreationException e) {
            log.error(e.getMessage(), e);
            Assert.fail("Metric Reporter creation failed");
        }
    }

    @Test(expectedExceptions = MetricReportingException.class, dependsOnMethods =
            {"testMetricReporterCreation"})
    public void testMetricReporterCreationWithoutAttributes() throws MetricCreationException,
                                                                     MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "some_key");
        createAndPublish(configs, null);
    }

    @Test(expectedExceptions = MetricReportingException.class, dependsOnMethods =
            {"testMetricReporterCreationWithoutAttributes"})
    public void testMetricReporterCreationWithMissingAttributes() throws MetricCreationException,
                                                                         MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "some_key");

        Map<String, String> event = new HashMap<>();
        event.put("correlationId", "12345");
        event.put("keyType", "pub_key");
        event.put("apiId", "121");
        createAndPublish(configs, event);
    }

    @Test(expectedExceptions = MetricReportingException.class, dependsOnMethods =
            {"testMetricReporterCreationWithMissingAttributes"})
    public void testMetricReporterCreationWithNullAttributes() throws MetricCreationException,
                                                                      MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "some_key");

        Map<String, String> event = new HashMap<>();
        event.put("correlationId", "12345");
        event.put("keyType", "pub_key");
        event.put("apiId", "121");
        event.put("apiName", "pizzashak");
        event.put("apiContext", "/pizzashak");
        event.put("apiVersion", "1.0.0");
        event.put("responseCode", "200");
        event.put("apiCreator", "admin");
        event.put("apiMethod", "GET");
        event.put("apiCreatorTenantDomain", "carbon.super");
        event.put("destination", "localhost");
        event.put("applicationId", "5");
        event.put("applicationName", "pizza-app");
        event.put("applicationConsumerKey", "consumer_key");
        event.put("applicationOwner", "admin");
        event.put("regionId", "2");
        event.put("gatewayType", "synapse");
        event.put("userAgent", "Mozilla");
        event.put("responseCacheHit", "false");
        event.put("responseLatency", "2000");
        event.put("requestMediationLatency", "250");
        event.put("responseMediationLatency", "1750");
        event.put("deploymentId", null);
        createAndPublish(configs, event);
    }

    @Test(expectedExceptions = MetricReportingException.class, dependsOnMethods =
            {"testMetricReporterCreationWithNullAttributes"})
    public void testMetricReporterCreationWithEmptyAttributes() throws MetricCreationException,
                                                                       MetricReportingException {
        Map<String, String> configs = new HashMap<>();
        configs.put("token.api.url", "localhost/token-api");
        configs.put("auth.api.url", "localhost/auth-api");
        configs.put("consumer.secret", "some_secret");
        configs.put("consumer.key", "some_key");

        Map<String, String> event = new HashMap<>();
        event.put("correlationId", "12345");
        event.put("keyType", "pub_key");
        event.put("apiId", "121");
        event.put("apiName", "pizzashak");
        event.put("apiContext", "/pizzashak");
        event.put("apiVersion", "1.0.0");
        event.put("responseCode", "200");
        event.put("apiCreator", "admin");
        event.put("apiMethod", "GET");
        event.put("apiCreatorTenantDomain", "carbon.super");
        event.put("destination", "localhost");
        event.put("applicationId", "5");
        event.put("applicationName", "pizza-app");
        event.put("applicationConsumerKey", "consumer_key");
        event.put("applicationOwner", "admin");
        event.put("regionId", "2");
        event.put("gatewayType", "synapse");
        event.put("userAgent", "Mozilla");
        event.put("responseCacheHit", "false");
        event.put("responseLatency", "2000");
        event.put("requestMediationLatency", "250");
        event.put("responseMediationLatency", "1750");
        event.put("deploymentId", "");
        createAndPublish(configs, event);
    }

    /**
     * Helper method to create and publish metrics for testing purposes
     *
     * @param configs Config map
     * @param event   Event map
     * @throws MetricCreationException  Thrown if config properties are missing
     * @throws MetricReportingException Thrown if event properties are missing
     */
    private void createAndPublish(Map<String, String> configs, Map<String, String> event)
            throws MetricCreationException, MetricReportingException {
        MetricReporter metricReporter = MetricReporterFactory.getInstance().createMetricReporter(null, configs);
        CounterMetric counterMetric = metricReporter.createCounterMetric("apim.response", MetricSchema.RESPONSE);


        counterMetric.incrementCount(event);
    }
}
