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

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.options.Option;
import com.mashape.unirest.http.options.Options;
import com.moesif.api.MoesifAPIClient;
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.core.LoggerContext;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.client.SimpleMoesifClient;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.MoesifReporter;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifProxyUtils;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.TestHttpProxy;
import org.wso2.am.analytics.publisher.util.UnitTestAppender;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for routing Moesif SDK traffic through an HTTP proxy using {@link MoesifProxyUtils}.
 */
public class MoesifProxyUtilsTestCase {

    // .invalid never resolves, so a request can only succeed if it is sent through the proxy.
    private static final String TARGET_BASE_URL = "http://moesif-proxy-test.invalid";
    private static final String PROXY_USERNAME = "proxyUser";
    private static final String PROXY_PASSWORD = "proxyPass";
    private static final int TIMEOUT_SECONDS = 10;

    @AfterMethod
    public void resetUnirest() throws IOException {

        // Unirest keeps its clients in static state, so restore the defaults after every test.
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
    public void testProxyNotConfiguredWhenDisabled() throws MetricCreationException {

        Object asyncClientBefore = Options.getOption(Option.ASYNCHTTPCLIENT);

        MoesifProxyUtils.configureProxyIfEnabled(new HashMap<>());
        Map<String, String> properties = proxyProperties("127.0.0.1", "3128");
        properties.put(Constants.PROXY_ENABLE, "false");
        MoesifProxyUtils.configureProxyIfEnabled(properties);

        Assert.assertNull(Options.getOption(Option.PROXY), "Proxy should not be set when proxy is disabled");
        Assert.assertSame(Options.getOption(Option.ASYNCHTTPCLIENT), asyncClientBefore,
                "Unirest async client should not be replaced when proxy is disabled");
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testMissingProxyHost() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("", "3128"));
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testInvalidProxyPort() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", "abc"));
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testProxyPortBelowRange() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", "0"));
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testProxyPortAboveRange() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", "65536"));
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testMissingProxyPort() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", ""));
    }

    @Test
    public void testProxyHostIsSetWithDefaultProtocol() throws MetricCreationException {

        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", "3128"));

        Assert.assertEquals(Options.getOption(Option.PROXY), new HttpHost("127.0.0.1", 3128, "http"));
    }

    @Test
    public void testProxyHostIsSetWithConfiguredProtocol() throws MetricCreationException {

        Map<String, String> properties = proxyProperties("127.0.0.1", "3128");
        properties.put(Constants.PROXY_PROTOCOL, "https");
        MoesifProxyUtils.configureProxyIfEnabled(properties);

        Assert.assertEquals(Options.getOption(Option.PROXY), new HttpHost("127.0.0.1", 3128, "https"));
    }

    @Test
    public void testRequestSentThroughProxyWithoutAuthentication() throws Exception {

        try (TestHttpProxy proxy = new TestHttpProxy()) {
            MoesifProxyUtils.configureProxyIfEnabled(
                    proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort())));

            HttpResponse<String> response = Unirest.post(TARGET_BASE_URL + "/v1/events").body("{}")
                    .asStringAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Assert.assertEquals(response.getStatus(), 200);
            List<TestHttpProxy.RecordedRequest> requests = proxy.getRequests();
            Assert.assertEquals(requests.size(), 1);
            Assert.assertEquals(requests.get(0).getRequestLine(),
                    "POST " + TARGET_BASE_URL + "/v1/events HTTP/1.1");
            Assert.assertNull(requests.get(0).getHeader("Proxy-Authorization"),
                    "No proxy credentials should be sent when none are configured");
        }
    }

    @Test
    public void testRequestSentThroughProxyWithBasicAuthentication() throws Exception {

        try (TestHttpProxy proxy = new TestHttpProxy(PROXY_USERNAME, PROXY_PASSWORD)) {
            Map<String, String> properties = proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort()));
            properties.put(Constants.PROXY_USERNAME, PROXY_USERNAME);
            properties.put(Constants.PROXY_PASSWORD, PROXY_PASSWORD);
            MoesifProxyUtils.configureProxyIfEnabled(properties);

            HttpResponse<String> response = Unirest.post(TARGET_BASE_URL + "/v1/events").body("{}")
                    .asStringAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Assert.assertEquals(response.getStatus(), 200, "Request should succeed after the 407 challenge");
            List<TestHttpProxy.RecordedRequest> requests = proxy.getRequests();
            Assert.assertFalse(requests.isEmpty());
            Assert.assertEquals(requests.get(requests.size() - 1).getHeader("Proxy-Authorization"),
                    basicAuth(PROXY_USERNAME, PROXY_PASSWORD));
        }
    }

    @Test
    public void testRequestRejectedWithWrongProxyCredentials() throws Exception {

        try (TestHttpProxy proxy = new TestHttpProxy(PROXY_USERNAME, PROXY_PASSWORD)) {
            Map<String, String> properties = proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort()));
            properties.put(Constants.PROXY_USERNAME, PROXY_USERNAME);
            properties.put(Constants.PROXY_PASSWORD, "wrongPassword");
            MoesifProxyUtils.configureProxyIfEnabled(properties);

            HttpResponse<String> response = Unirest.post(TARGET_BASE_URL + "/v1/events").body("{}")
                    .asStringAsync().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Assert.assertEquals(response.getStatus(), 407);
        }
    }

    @Test
    public void testMoesifSdkPublishesThroughProxy() throws Exception {

        try (TestHttpProxy proxy = new TestHttpProxy()) {
            MoesifProxyUtils.configureProxyIfEnabled(
                    proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort())));

            MoesifAPIClient moesifAPIClient = new MoesifAPIClient("test-application-id", TARGET_BASE_URL);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Integer> statusCode = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            moesifAPIClient.getAPI().createEventsBatchAsync(Collections.emptyList(),
                    new APICallBack<com.moesif.api.http.response.HttpResponse>() {
                        @Override
                        public void onSuccess(HttpContext context,
                                              com.moesif.api.http.response.HttpResponse response) {

                            statusCode.set(context.getResponse().getStatusCode());
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(HttpContext context, Throwable error) {

                            failure.set(error);
                            latch.countDown();
                        }
                    });

            Assert.assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Moesif callback was not invoked");
            Assert.assertNull(failure.get(), "Publishing through the proxy failed");
            Assert.assertEquals(statusCode.get(), Integer.valueOf(200));
            // Besides the events batch, the SDK also fetches /v1/config and /v1/rules on client creation.
            // All of them must go through the proxy.
            List<TestHttpProxy.RecordedRequest> requests = proxy.getRequests();
            assertAllSentThroughProxy(requests);
            Assert.assertTrue(requests.stream().anyMatch(r -> r.getRequestLine()
                            .equals("POST " + TARGET_BASE_URL + "/v1/events/batch HTTP/1.1")),
                    "Events batch was not sent through the proxy");
            for (TestHttpProxy.RecordedRequest request : requests) {
                Assert.assertEquals(request.getHeader("X-Moesif-Application-Id"), "test-application-id");
            }
        }
    }

    @Test
    public void testMoesifSdkPublishesThroughProxyWithBasicAuthentication() throws Exception {

        // The SDK sends with Unirest's synchronous client, so this covers the sync client built for proxy auth.
        try (TestHttpProxy proxy = new TestHttpProxy(PROXY_USERNAME, PROXY_PASSWORD)) {
            Map<String, String> properties = proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort()));
            properties.put(Constants.PROXY_USERNAME, PROXY_USERNAME);
            properties.put(Constants.PROXY_PASSWORD, PROXY_PASSWORD);
            MoesifProxyUtils.configureProxyIfEnabled(properties);

            MoesifAPIClient moesifAPIClient = new MoesifAPIClient("test-application-id", TARGET_BASE_URL);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Integer> statusCode = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            moesifAPIClient.getAPI().createEventsBatchAsync(Collections.emptyList(),
                    new APICallBack<com.moesif.api.http.response.HttpResponse>() {
                        @Override
                        public void onSuccess(HttpContext context,
                                              com.moesif.api.http.response.HttpResponse response) {

                            statusCode.set(context.getResponse().getStatusCode());
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(HttpContext context, Throwable error) {

                            failure.set(error);
                            latch.countDown();
                        }
                    });

            Assert.assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Moesif callback was not invoked");
            Assert.assertNull(failure.get(), "Publishing through the authenticated proxy failed");
            Assert.assertEquals(statusCode.get(), Integer.valueOf(200));
            List<TestHttpProxy.RecordedRequest> requests = proxy.getRequests();
            TestHttpProxy.RecordedRequest batchRequest = requests.stream()
                    .filter(r -> r.getRequestLine().startsWith("POST " + TARGET_BASE_URL + "/v1/events/batch"))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("Events batch was not sent through the proxy"));
            Assert.assertEquals(batchRequest.getHeader("Proxy-Authorization"),
                    basicAuth(PROXY_USERNAME, PROXY_PASSWORD));
        }
    }

    @Test
    public void testMoesifReporterConfiguresProxy() throws Exception {

        try (TestHttpProxy proxy = new TestHttpProxy()) {
            Map<String, String> properties = proxyProperties(proxy.getHost(), String.valueOf(proxy.getPort()));
            properties.put(Constants.TYPE, Constants.MOESIF);
            properties.put(Constants.MOESIF_KEY, "test-application-id");
            properties.put(Constants.MOESIF_BASE_URL, TARGET_BASE_URL);
            properties.put(Constants.WORKER_THREAD_COUNT, "1");

            new MoesifReporter(properties);

            Assert.assertEquals(Options.getOption(Option.PROXY),
                    new HttpHost(proxy.getHost(), proxy.getPort(), "http"));
            // The SDK client created by the reporter fetches its config on startup, which must use the proxy.
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
            while (proxy.getRequests().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            Assert.assertFalse(proxy.getRequests().isEmpty(), "Moesif SDK did not send any request via the proxy");
            assertAllSentThroughProxy(proxy.getRequests());
        }
    }

    @Test(expectedExceptions = MetricCreationException.class)
    public void testMoesifReporterFailsOnInvalidProxyConfig() throws MetricCreationException {

        Map<String, String> properties = proxyProperties("127.0.0.1", "abc");
        properties.put(Constants.TYPE, Constants.MOESIF);
        properties.put(Constants.MOESIF_KEY, "test-application-id");

        new MoesifReporter(properties);
    }

    @Test
    public void testPublishFailureWhenProxyIsDown() throws Exception {

        // Reserve a free port and release it, so that nothing is listening on it.
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        MoesifProxyUtils.configureProxyIfEnabled(proxyProperties("127.0.0.1", String.valueOf(closedPort)));

        UnitTestAppender appender = LoggerContext.getContext(false).getConfiguration()
                .getAppender("UnitTestAppender");
        List<Throwable> uncaught = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.add(throwable));
        try {
            SimpleMoesifClient client = new SimpleMoesifClient("test-application-id", TARGET_BASE_URL);
            MetricEventBuilder builder = Mockito.mock(MetricEventBuilder.class);
            Mockito.when(builder.build()).thenReturn(validResponseData());
            client.publishBatch(Collections.singletonList(builder));

            // The Moesif callback has no response when the connection fails. It must be handled as a retryable
            // failure (status 0) instead of throwing a NullPointerException. The retry queue is not enabled here.
            String expectedLog = "(status 0); retry queue disabled, events will be dropped";
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
            while (!appender.checkContains(expectedLog) && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            Assert.assertTrue(appender.checkContains(expectedLog), "Connection failure was not logged");
            Assert.assertTrue(uncaught.isEmpty(), "Uncaught exception in Moesif callback: " + uncaught);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }
    }

    private static Map<String, Object> validResponseData() {

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
        return data;
    }

    private static Map<String, String> proxyProperties(String host, String port) {

        Map<String, String> properties = new HashMap<>();
        properties.put(Constants.PROXY_ENABLE, "true");
        properties.put(Constants.PROXY_HOST, host);
        properties.put(Constants.PROXY_PORT, port);
        return properties;
    }

    private static void assertAllSentThroughProxy(List<TestHttpProxy.RecordedRequest> requests) {

        // A proxied plain HTTP request carries the absolute target URI in its request line.
        for (TestHttpProxy.RecordedRequest request : requests) {
            String requestLine = request.getRequestLine();
            Assert.assertTrue(requestLine.matches("(GET|POST) " + TARGET_BASE_URL.replace(".", "\\.") + "/.*"),
                    "Unexpected request line: " + requestLine);
        }
    }

    private static String basicAuth(String username, String password) {

        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
