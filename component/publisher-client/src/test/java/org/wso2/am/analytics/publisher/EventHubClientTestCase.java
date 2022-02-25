/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.client.EventHubProducerClientFactory;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricReporter;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;
import org.wso2.am.analytics.publisher.reporter.cloud.DefaultAnalyticsMetricReporter;
import org.wso2.am.analytics.publisher.util.AuthAPIMockService;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.TestUtils;
import org.wso2.am.analytics.publisher.util.UnitTestAppender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests related to publisher client and AMQP producer.
 */
public class EventHubClientTestCase extends AuthAPIMockService {

    private EventHubProducerClient client;
    private Map<String, String> configs;
    private UnitTestAppender appender;
    private MockedStatic<EventHubProducerClientFactory> clientFactoryMocked;

    @BeforeClass
    public void init() {
        clientFactoryMocked = Mockito.mockStatic(EventHubProducerClientFactory.class);
    }

    @BeforeMethod
    public void setup() {

        client = Mockito.mock(EventHubProducerClient.class);
        clientFactoryMocked.when(() -> EventHubProducerClientFactory
                .create(any(String.class), any(String.class), any(AmqpRetryOptions.class), anyMap()))
                .thenReturn(client);

        String authToken = UUID.randomUUID().toString();
        mock(200, authToken);

        configs = new HashMap<>();
        configs.put(Constants.AUTH_API_URL, authApiEndpoint);
        configs.put(Constants.AUTH_API_TOKEN, authToken);

        LoggerContext context = LoggerContext.getContext(false);
        Configuration config = context.getConfiguration();
        appender = config.getAppender("UnitTestAppender");
    }

    @Test
    public void testEventPublishing() throws Exception {
        EventDataBatch eventDataBatch = Mockito.mock(EventDataBatch.class);
        EventDataBatch newEventDataBatch = Mockito.mock(EventDataBatch.class);
        when(client.createBatch()).thenReturn(eventDataBatch).thenReturn(newEventDataBatch);
        when(eventDataBatch.tryAdd(any(EventData.class))).thenReturn(true);
        when(eventDataBatch.getCount()).thenReturn(1);
        when(newEventDataBatch.getCount()).thenReturn(0);

        MetricReporter metricReporter = new DefaultAnalyticsMetricReporter(configs);
        CounterMetric metric = metricReporter.createCounterMetric("test-connection-counter", MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        TestUtils.populateBuilder(builder);

        // try publishing an event
        metric.incrementCount(builder);

        // waiting to worker thread adding event to the queue
        verify(client, timeout(20000).times(1)).send(any(EventDataBatch.class));
    }

    @Test
    public void testEventPublishingWithAMQPAuthException() throws Exception {

        EventDataBatch eventDataBatch = Mockito.mock(EventDataBatch.class);
        when(client.createBatch()).thenReturn(eventDataBatch);
        when(eventDataBatch.tryAdd(any(EventData.class))).thenReturn(true);
        when(eventDataBatch.getCount()).thenReturn(1);

        doThrow(new AmqpException(false, AmqpErrorCondition.UNAUTHORIZED_ACCESS, "", null))
                .when(client)
                .send(any(EventDataBatch.class));

        MetricReporter metricReporter = new DefaultAnalyticsMetricReporter(configs);
        CounterMetric metric = metricReporter.createCounterMetric("test-connection-counter1", MetricSchema.RESPONSE);
        MetricEventBuilder builder = metric.getEventBuilder();
        TestUtils.populateBuilder(builder);

        // try publishing an event
        metric.incrementCount(builder);

        // waiting to adding event to the queue
        verify(eventDataBatch, timeout(10000).times(1)).tryAdd(any(EventData.class));

        // waiting to flushing thread try to send
        verify(client, timeout(20000).times(1)).send(any(EventDataBatch.class));

        // verify flushing thread identified the auth error when try to send via AMQP
        Thread.sleep(1000);
        List<String> messages = appender.getMessages();
        Assert.assertTrue(TestUtils
                .isContains(messages, "Marked client status as FLUSHING_FAILED due to AMQP authentication failure."));

        // Try to publish another event
        metric.incrementCount(builder);

        // verify it is also trying to add event queue, after identified state as FLUSHING_FAILED
        verify(eventDataBatch, timeout(10000).times(2)).tryAdd(any(EventData.class));

        // verify worker thread has already identified the FLUSHING_FAILED state
        messages = appender.getMessages();
        Assert.assertTrue(TestUtils.isContains(messages, "client status is FLUSHING_FAILED. Producer client "
                + "will be re-initialized retaining the Event Data Batch"));
    }

}
