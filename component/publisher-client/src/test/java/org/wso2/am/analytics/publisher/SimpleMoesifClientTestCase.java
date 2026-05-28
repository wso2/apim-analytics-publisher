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

import com.moesif.api.MoesifAPIClient;
import com.moesif.api.controllers.APIController;
import com.moesif.api.models.EventModel;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.client.SimpleMoesifClient;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.MoesifRetryBuffer;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.RetryConfig;
import org.wso2.am.analytics.publisher.util.Constants;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for {@link SimpleMoesifClient}. Uses Mockito's construction mocking to replace the
 * real {@link MoesifAPIClient} so no network calls happen, and exercises {@code buildEventResponse},
 * {@code publishBatch}, and the retry-buffer stash path.
 */
public class SimpleMoesifClientTestCase {

    private MockedConstruction<MoesifAPIClient> mockedClients;
    private APIController mockApi;
    private ScheduledExecutorService scheduler;

    @BeforeMethod
    public void setUp() {
        mockApi = mock(APIController.class);
        mockedClients = Mockito.mockConstruction(MoesifAPIClient.class,
                (mock, ctx) -> when(mock.getAPI()).thenReturn(mockApi));
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterMethod
    public void tearDown() {
        if (mockedClients != null) {
            mockedClients.close();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // ----- constructors -----

    @Test
    public void testConstructWithKeyOnly() {
        Assert.assertNotNull(new SimpleMoesifClient("k"));
    }

    @Test
    public void testConstructWithKeyAndUrl() {
        Assert.assertNotNull(new SimpleMoesifClient("k", "http://localhost"));
    }

    @Test
    public void testConstructWithKeyEmptyUrl() {
        Assert.assertNotNull(new SimpleMoesifClient("k", ""));
    }

    @Test
    public void testConstructWithRetryConfig() throws Exception {
        RetryConfig cfg = new RetryConfig(100, 3600L, 10, 3, 50L, scheduler);
        SimpleMoesifClient client = new SimpleMoesifClient("k", null, null, cfg);
        Assert.assertNotNull(retryBuffer(client));
    }

    // ----- buildEventResponse: non-error branch -----

    @Test
    public void testBuildEventResponseHappyPath() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        EventModel ev = client.buildEventResponse(data);
        Assert.assertNotNull(ev);
        Assert.assertNotNull(ev.getRequest());
        Assert.assertNotNull(ev.getResponse());
        Assert.assertEquals(ev.getUserId(), "alice");
        Assert.assertEquals(ev.getRequest().getVerb(), "GET");
        Assert.assertEquals(ev.getResponse().getStatus(), 200);
    }

    @Test
    public void testBuildEventResponseStripsCarbonSuper() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        data.put(Constants.USER_NAME, "admin@carbon.super");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getUserId(), "admin");
    }

    @Test
    public void testBuildEventResponseFallsBackToUnknownWhenUserMissing() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        data.remove(Constants.USER_NAME);
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getUserId(), Constants.UNKNOWN_VALUE);
    }

    @Test
    public void testBuildEventResponseReadsUserFromProperties() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        data.remove(Constants.USER_NAME);
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> props = (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
        props.put(Constants.USER_NAME, "bob");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getUserId(), "bob");
    }

    @Test
    public void testBuildEventResponseGatewayUrlOverridesUri() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> props = (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
        props.put(Constants.GATEWAY_URL, "https://gw.example.com/api/v1");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getRequest().getUri(), "https://gw.example.com/api/v1");
    }

    @Test
    public void testBuildEventResponseIncludesRequestHeaders() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> props = (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("X-Custom", "abc");
        props.put(Constants.REQUEST_HEADERS, reqHeaders);
        Map<String, String> respHeaders = new HashMap<>();
        respHeaders.put("X-Response", "xyz");
        props.put(Constants.RESPONSE_HEADERS, respHeaders);
        EventModel ev = client.buildEventResponse(data);
        Assert.assertNotNull(ev.getRequest().getHeaders());
        Assert.assertTrue(ev.getRequest().getHeaders().containsKey("X-Custom"));
    }

    @Test
    public void testBuildEventResponsePopulatesAiMetadata() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validResponseData();
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> props = (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
        props.put(Constants.AI_METADATA, "ai-md");
        props.put(Constants.AI_TOKEN_USAGE, "tokens");
        props.put(Constants.IS_EGRESS, true);
        props.put(Constants.SUBTYPE, "chat");
        props.put(Constants.IS_GUARDRAIL_HIT, false);
        props.put(Constants.GUARDRAIL_NAME, "none");
        props.put(Constants.MCP_ANALYTICS, "mcp");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertNotNull(ev.getMetadata());
        @SuppressWarnings("unchecked")
        Map<String, Object> md = (Map<String, Object>) ev.getMetadata();
        Assert.assertEquals(md.get(Constants.AI_METADATA), "ai-md");
        Assert.assertEquals(md.get(Constants.MCP_ANALYTICS), "mcp");
    }

    // ----- buildEventResponse: error branch -----

    @Test
    public void testBuildEventResponseErrorBranchWithGatewayUrl() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validErrorData();
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> props = (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
        props.put(Constants.GATEWAY_URL, "https://gw.example.com/x");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getRequest().getUri(), "https://gw.example.com/x");
    }

    @Test
    public void testBuildEventResponseErrorBranchWithApiContextFallback() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validErrorData();
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getRequest().getUri(), "/api/v1/{x}");
    }

    @Test
    public void testBuildEventResponseErrorBranchFallsBackToNotApplicable() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validErrorData();
        data.remove(Constants.API_RESOURCE_TEMPLATE);
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getRequest().getUri(), Constants.NOT_APPLICABLE);
    }

    @Test
    public void testBuildEventResponseErrorBranchFallsBackVerbToNotApplicable() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        Map<String, Object> data = validErrorData();
        data.put(Constants.API_METHOD, "");
        EventModel ev = client.buildEventResponse(data);
        Assert.assertEquals(ev.getRequest().getVerb(), Constants.NOT_APPLICABLE);
    }

    // ----- publishBatch paths -----

    @Test
    public void testPublishBatchNullReturnsImmediately() {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        client.publishBatch(null);
        verifyNoInteractions(mockApi);
    }

    @Test
    public void testPublishBatchEmptyReturnsImmediately() {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        client.publishBatch(Collections.emptyList());
        verifyNoInteractions(mockApi);
    }

    @Test
    public void testPublishBatchSingleEventCallsCreateEventAsync() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        MetricEventBuilder builder = mockBuilder(validResponseData());
        client.publishBatch(Collections.singletonList(builder));
        verify(mockApi).createEventAsync(any(EventModel.class), any());
    }

    @Test
    public void testPublishBatchMultipleEventsCallsCreateEventsBatchAsync() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        List<MetricEventBuilder> builders = java.util.Arrays.asList(
                mockBuilder(validResponseData()),
                mockBuilder(validResponseData()));
        client.publishBatch(builders);
        verify(mockApi).createEventsBatchAsync(anyList(), any());
    }

    @Test
    public void testPublishBatchStashesWhenBufferUnhealthy() throws Exception {
        RetryConfig cfg = new RetryConfig(100, 3600L, 10, 3, 50L, scheduler);
        SimpleMoesifClient client = new SimpleMoesifClient("k", null, null, cfg);

        MoesifRetryBuffer buffer = retryBuffer(client);
        forceUnhealthy(buffer);

        MetricEventBuilder builder = mockBuilder(validResponseData());
        client.publishBatch(Collections.singletonList(builder));

        verify(mockApi, never()).createEventAsync(any(EventModel.class), any());
        verify(mockApi, never()).createEventsBatchAsync(anyList(), any());
        Assert.assertTrue(currentCount(buffer) > 0);
    }

    @Test
    public void testPublishSingleEventCallsCreateEventAsync() throws Exception {
        SimpleMoesifClient client = new SimpleMoesifClient("k");
        MetricEventBuilder builder = mockBuilder(validResponseData());
        client.publish(builder);
        verify(mockApi).createEventAsync(any(EventModel.class), any());
    }

    @Test
    public void testPublishStashesWhenBufferUnhealthy() throws Exception {
        RetryConfig cfg = new RetryConfig(100, 3600L, 10, 3, 50L, scheduler);
        SimpleMoesifClient client = new SimpleMoesifClient("k", null, null, cfg);

        MoesifRetryBuffer buffer = retryBuffer(client);
        forceUnhealthy(buffer);

        MetricEventBuilder builder = mockBuilder(validResponseData());
        client.publish(builder);

        verify(mockApi, never()).createEventAsync(any(EventModel.class), any());
        Assert.assertTrue(currentCount(buffer) > 0);
    }

    // ----- helpers -----

    private static MetricEventBuilder mockBuilder(Map<String, Object> data) throws MetricReportingException {
        MetricEventBuilder builder = mock(MetricEventBuilder.class);
        when(builder.build()).thenReturn(data);
        return builder;
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
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.API_CONTEXT, "/api/v1");
        data.put(Constants.PROPERTIES, props);
        return data;
    }

    private static Map<String, Object> validErrorData() {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.ERROR_CODE, 500);
        data.put(Constants.ERROR_MESSAGE, "boom");
        data.put(Constants.ERROR_TYPE, "Backend");
        data.put(Constants.REQUEST_TIMESTAMP, Instant.now().toString());
        data.put(Constants.API_METHOD, "POST");
        data.put(Constants.API_VERSION, "1.0.0");
        data.put(Constants.API_RESOURCE_TEMPLATE, "/{x}");
        data.put(Constants.PROXY_RESPONSE_CODE, 500);
        data.put(Constants.USER_NAME, "alice");
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.API_CONTEXT, "/api/v1");
        data.put(Constants.PROPERTIES, props);
        return data;
    }

    private static MoesifRetryBuffer retryBuffer(SimpleMoesifClient client) throws Exception {
        Field f = SimpleMoesifClient.class.getDeclaredField("retryBuffer");
        f.setAccessible(true);
        return (MoesifRetryBuffer) f.get(client);
    }

    private static void forceUnhealthy(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("healthy");
        f.setAccessible(true);
        ((AtomicBoolean) f.get(buffer)).set(false);
    }

    private static int currentCount(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("currentEventCount");
        f.setAccessible(true);
        return f.getInt(buffer);
    }
}
