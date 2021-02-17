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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.client.EventHubClient;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricReporter;
import org.wso2.am.analytics.publisher.reporter.MetricReporterFactory;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.TestUtils;
import org.wso2.am.analytics.publisher.util.UnitTestAppender;

import java.util.HashMap;
import java.util.Map;

public class ErrorHandlingTestCase {

    @Test
    public void testConnectionInvalidURL() throws MetricCreationException, MetricReportingException {
        boolean errorOccurred = false;
        String message = "";
        UnitTestAppender appender = new UnitTestAppender();
        Logger log = Logger.getLogger(EventHubClient.class);
        log.addAppender(appender);

        Map<String, String> configMap = new HashMap<>();
        configMap.put(Constants.AUTH_API_URL, "some_url");
        configMap.put(Constants.AUTH_API_TOKEN, "some_token");
        MetricReporter metricReporter = MetricReporterFactory.getInstance().createMetricReporter(configMap);
        CounterMetric metric = metricReporter.createCounterMetric("test-connection-counter", MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        builder.addAttribute("some_key", "some_value");
        try {
            metric.incrementCount(builder);
        } catch (MetricReportingException e) {
            errorOccurred = true;
            message = e.getMessage();
        }
        Assert.assertTrue(errorOccurred, "MetricReportingException should be thrown");
        Assert.assertTrue(message.contains("Eventhub Client is not connected."), "Expected error hasn't thrown.");
        Assert.assertTrue(appender.checkContains("Unrecoverable error occurred when creating Eventhub "
                                                         + "Client"), "Expected error hasn't logged in the "
                                  + "EventHubClientClass");
    }

    @Test(dependsOnMethods = {"testConnectionInvalidURL"})
    public void testConnectionUnavailability() throws MetricCreationException, MetricReportingException,
                                                      InterruptedException {
        UnitTestAppender appender = new UnitTestAppender();
        Logger log = Logger.getLogger(EventHubClient.class);
        log.setLevel(Level.DEBUG);
        log.addAppender(appender);

        Map<String, String> configMap = new HashMap<>();
        configMap.put(Constants.AUTH_API_URL, "https://localhost:1234/non-existance");
        configMap.put(Constants.AUTH_API_TOKEN, "some_token");
        MetricReporterFactory factory = MetricReporterFactory.getInstance();
        factory.reset();
        MetricReporter metricReporter = factory.createMetricReporter(configMap);
        CounterMetric metric = metricReporter.createCounterMetric("test-connection-counter", MetricSchema.RESPONSE);
        Assert.assertTrue(appender.checkContains("Recoverable error occurred when creating Eventhub Client. "
                                                         + "Retry attempts will be made"));
        Assert.assertTrue(appender.checkContains("Provided authentication endpoint "
                                                         + "https://localhost:1234/non-existance is not "
                                                         + "reachable."));
        MetricEventBuilder builder = metric.getEventBuilder();
        TestUtils.populateBuilder(builder);
        metric.incrementCount(builder);
        Thread.sleep(1000);
        Assert.assertTrue(appender.checkContains("will be parked as EventHub Client is inactive."), "Thread "
                + "waiting log entry has not printed.");
    }
}
