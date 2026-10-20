/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.am.analytics.publisher;

import com.mashape.unirest.http.options.Option;
import com.mashape.unirest.http.options.Options;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.client.SimpleMoesifClient;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.MoesifRetryBuffer;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.RetryConfig;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifProxyUtils;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.TestHttpProxy;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Tests how failed Moesif publishes are retried through the proxy, with and without the retry buffer.
 */
public class MoesifPublishRetryTestCase {

    // .invalid never resolves, so requests only reach the test proxy.
    private static final String TARGET_BASE_URL = "http://moesif-retry-test.invalid";
    private static final String SINGLE_EVENT_REQUEST = "POST " + TARGET_BASE_URL + "/v1/events ";
    private static final String BATCH_REQUEST = "POST " + TARGET_BASE_URL + "/v1/events/batch ";
    private static final long RETRY_INTERVAL_SECONDS = 1;
    private static final int TIMEOUT_SECONDS = 10;

    private ScheduledExecutorService scheduler;

    @BeforeMethod
    public void createScheduler() {

        scheduler = Executors.newScheduledThreadPool(2);
    }

    @AfterMethod
    public void cleanUp() throws IOException {

        scheduler.shutdownNow();
        for (Option option : new Option[]{Option.HTTPCLIENT, Option.ASYNCHTTPCLIENT}) {
            Object client = Options.getOption(option);
            if (client instanceof Closeable) {
                ((Closeable) client).close();
            }
        }
        Options.setOption(Option.PROXY, null);
        Options.refresh();
    }

    @Test
    public void testRetryableFailureDroppedWhenRetryBufferDisabled() throws Exception {

        try (TestHttpProxy proxy = startProxy(503)) {
            SimpleMoesifClient client = new SimpleMoesifClient("test-application-id", TARGET_BASE_URL);

            client.publish(newBuilder());

            waitUntil(() -> countRequests(proxy, SINGLE_EVENT_REQUEST) >= 1);
            // Wait longer than a retry interval to make sure nothing is retried.
            Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS * 2));
            Assert.assertEquals(countRequests(proxy, SINGLE_EVENT_REQUEST), 1L);
            Assert.assertEquals(countRequests(proxy, BATCH_REQUEST), 0L);
        }
    }

    @Test
    public void testRetryBufferKeepsRetryingWhileMoesifIsDown() throws Exception {

        try (TestHttpProxy proxy = startProxy(503)) {
            SimpleMoesifClient client = newClientWithRetryBuffer();

            client.publish(newBuilder());

            // The failed event is queued and re-sent as a batch on every retry interval, without a retry limit.
            waitUntil(() -> countRequests(proxy, BATCH_REQUEST) >= 3);
            MoesifRetryBuffer buffer = retryBuffer(client);
            Assert.assertFalse(buffer.isHealthy());
            Assert.assertEquals(queuedEventCount(buffer), 1);
            Assert.assertEquals(countRequests(proxy, SINGLE_EVENT_REQUEST), 1L);
        }
    }

    @Test
    public void testQueuedEventsDeliveredWhenMoesifRecovers() throws Exception {

        try (TestHttpProxy proxy = startProxy(503)) {
            SimpleMoesifClient client = newClientWithRetryBuffer();
            MoesifRetryBuffer buffer = retryBuffer(client);

            client.publish(newBuilder());
            waitUntil(() -> !buffer.isHealthy());
            // While Moesif is unhealthy, new events are queued without being sent.
            client.publish(newBuilder());
            Assert.assertEquals(countRequests(proxy, SINGLE_EVENT_REQUEST), 1L);
            Assert.assertEquals(queuedEventCount(buffer), 2);

            proxy.setResponseStatus(200);

            waitUntil(() -> buffer.isHealthy() && queuedEventCount(buffer) == 0);
            Assert.assertEquals(countRequests(proxy, SINGLE_EVENT_REQUEST), 1L);
        }
    }

    @Test
    public void testQueuedBatchRejectedDuringRetryIsDropped() throws Exception {

        try (TestHttpProxy proxy = startProxy(503)) {
            SimpleMoesifClient client = newClientWithRetryBuffer();
            MoesifRetryBuffer buffer = retryBuffer(client);

            client.publish(newBuilder());
            waitUntil(() -> !buffer.isHealthy());

            // Moesif now rejects the queued batch, e.g. the key was revoked while it was unreachable.
            proxy.setResponseStatus(401);

            waitUntil(() -> buffer.isHealthy() && queuedEventCount(buffer) == 0);
            long batchRequests = countRequests(proxy, BATCH_REQUEST);
            Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS * 2));
            Assert.assertEquals(countRequests(proxy, BATCH_REQUEST), batchRequests,
                    "A rejected batch must not be retried");
        }
    }

    @Test
    public void testNonRetryableClientErrorNotRetried() throws Exception {

        try (TestHttpProxy proxy = startProxy(401)) {
            SimpleMoesifClient client = newClientWithRetryBuffer();

            client.publish(newBuilder());

            waitUntil(() -> countRequests(proxy, SINGLE_EVENT_REQUEST) >= 1);
            Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS * 2));
            MoesifRetryBuffer buffer = retryBuffer(client);
            Assert.assertEquals(countRequests(proxy, SINGLE_EVENT_REQUEST), 1L);
            Assert.assertEquals(countRequests(proxy, BATCH_REQUEST), 0L);
            Assert.assertTrue(buffer.isHealthy());
            Assert.assertEquals(queuedEventCount(buffer), 0);
        }
    }

    private static TestHttpProxy startProxy(int responseStatus) throws Exception {

        TestHttpProxy proxy = new TestHttpProxy();
        proxy.setResponseStatus(responseStatus);
        Map<String, String> properties = new HashMap<>();
        properties.put(Constants.PROXY_ENABLE, "true");
        properties.put(Constants.PROXY_HOST, proxy.getHost());
        properties.put(Constants.PROXY_PORT, String.valueOf(proxy.getPort()));
        MoesifProxyUtils.configureProxyIfEnabled(properties);
        return proxy;
    }

    private SimpleMoesifClient newClientWithRetryBuffer() {

        RetryConfig retryConfig = new RetryConfig(100, RETRY_INTERVAL_SECONDS, 10, 5, 50L, scheduler);
        return new SimpleMoesifClient("test-application-id", TARGET_BASE_URL, null, retryConfig);
    }

    private static MetricEventBuilder newBuilder() throws Exception {

        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_IP, "127.0.0.1");
        data.put(Constants.API_RESOURCE_TEMPLATE, "/{value}");
        data.put(Constants.RESPONSE_LATENCY, 2000L);
        data.put(Constants.REQUEST_TIMESTAMP, Instant.now().toString());
        data.put(Constants.API_METHOD, "GET");
        data.put(Constants.API_VERSION, "1.0.0");
        data.put(Constants.PROXY_RESPONSE_CODE, 200);
        data.put(Constants.USER_NAME, "alice");
        data.put(Constants.USER_AGENT_HEADER, "Mozilla/5.0");
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put(Constants.API_CONTEXT, "/api/v1");
        data.put(Constants.PROPERTIES, properties);
        MetricEventBuilder builder = Mockito.mock(MetricEventBuilder.class);
        Mockito.when(builder.build()).thenReturn(data);
        return builder;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Assert.assertTrue(condition.getAsBoolean(), "Condition not met within " + TIMEOUT_SECONDS + "s");
    }

    private static long countRequests(TestHttpProxy proxy, String requestLinePrefix) {

        return proxy.getRequests().stream().filter(r -> r.getRequestLine().startsWith(requestLinePrefix)).count();
    }

    private static MoesifRetryBuffer retryBuffer(SimpleMoesifClient client) throws Exception {

        Field field = SimpleMoesifClient.class.getDeclaredField("retryBuffer");
        field.setAccessible(true);
        return (MoesifRetryBuffer) field.get(client);
    }

    private static int queuedEventCount(MoesifRetryBuffer buffer) {

        try {
            Field field = MoesifRetryBuffer.class.getDeclaredField("currentEventCount");
            field.setAccessible(true);
            return field.getInt(buffer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
