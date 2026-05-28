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
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import com.moesif.api.http.response.HttpResponse;
import com.moesif.api.models.EventModel;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.MoesifRetryBuffer;
import org.wso2.am.analytics.publisher.reporter.moesif.retry.RetryConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link MoesifRetryBuffer}. Uses a real (but slow-tick) scheduler so the periodic
 * probe never fires during tests, and invokes the probe directly via reflection when needed.
 */
public class MoesifRetryBufferTestCase {

    private ScheduledExecutorService scheduler;
    private MoesifAPIClient mockClient;
    private APIController mockApi;

    @BeforeMethod
    public void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        mockClient = mock(MoesifAPIClient.class);
        mockApi = mock(APIController.class);
        when(mockClient.getAPI()).thenReturn(mockApi);
    }

    @AfterMethod
    public void tearDown() {
        scheduler.shutdownNow();
    }

    private MoesifRetryBuffer buildBuffer(int capacityEvents) {
        return buildBuffer(capacityEvents, "test-moesif-key-abcd");
    }

    private MoesifRetryBuffer buildBuffer(int capacityEvents, String key) {
        // 1-hour interval so the periodic probe never fires inside test duration.
        RetryConfig cfg = new RetryConfig(capacityEvents, 3600L, 10, 3, 50L, scheduler);
        return new MoesifRetryBuffer(key, mockClient, cfg);
    }

    private List<EventModel> events(int count) {
        List<EventModel> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new EventModel());
        }
        return list;
    }

    @Test
    public void testInitiallyHealthy() {
        MoesifRetryBuffer buffer = buildBuffer(100);
        Assert.assertTrue(buffer.isHealthy());
    }

    @Test
    public void testStashStoresBatch() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.stash(events(5));
        Assert.assertEquals(currentCount(buffer), 5);
        Assert.assertEquals(stash(buffer).size(), 1);
    }

    @Test
    public void testStashNullAndEmptyAreNoOp() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(10);
        buffer.stash(null);
        buffer.stash(Collections.emptyList());
        Assert.assertEquals(currentCount(buffer), 0);
        Assert.assertEquals(stash(buffer).size(), 0);
    }

    @Test
    public void testStashEvictsOldestWhenOverCapacity() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(10);
        buffer.stash(events(4));   // 4
        buffer.stash(events(4));   // 8
        buffer.stash(events(4));   // would be 12; evict oldest batch (4) → 8
        Assert.assertEquals(currentCount(buffer), 8);
        Assert.assertEquals(stash(buffer).size(), 2);
        Assert.assertTrue(droppedCount(buffer) >= 4);
    }

    @Test
    public void testStashOversizedBatchDroppedEntirely() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(10);
        buffer.stash(events(20));
        Assert.assertEquals(currentCount(buffer), 0);
        Assert.assertEquals(stash(buffer).size(), 0);
        Assert.assertEquals(droppedCount(buffer), 20);
    }

    @Test
    public void testOnRetryableFailureFlipsUnhealthyAndStashes() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(3));
        Assert.assertFalse(buffer.isHealthy());
        Assert.assertEquals(currentCount(buffer), 3);
    }

    @Test
    public void testOnSuccessFromHealthyIsNoOp() {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onSuccess();
        Assert.assertTrue(buffer.isHealthy());
    }

    @Test
    public void testOnSuccessRecoversAfterFailure() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        Assert.assertFalse(buffer.isHealthy());
        buffer.onSuccess();
        Assert.assertTrue(buffer.isHealthy());
    }

    @Test
    public void testRepeatedFailuresDoNotReFlipHealthy() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(1));
        buffer.onRetryableFailure(events(1));
        buffer.onRetryableFailure(events(1));
        Assert.assertFalse(buffer.isHealthy());
        Assert.assertEquals(currentCount(buffer), 3);
    }

    @Test
    public void testProbeSendsOldestBatchToSdk() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        invokePrivate(buffer, "safeProbe");
        verify(mockApi, atLeastOnce()).createEventsBatchAsync(anyList(), any());
    }

    @Test
    public void testProbeSuccessTriggersRecovery() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        invokePrivate(buffer, "safeProbe");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<APICallBack<HttpResponse>> captor =
                (ArgumentCaptor<APICallBack<HttpResponse>>) (ArgumentCaptor) ArgumentCaptor.forClass(APICallBack.class);
        verify(mockApi).createEventsBatchAsync(anyList(), captor.capture());

        HttpContext ctx = mock(HttpContext.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(ctx.getResponse()).thenReturn(resp);
        when(resp.getStatusCode()).thenReturn(200);

        captor.getValue().onSuccess(ctx, resp);

        Assert.assertTrue(buffer.isHealthy());
    }

    @Test
    public void testProbeRetryableStatusRestoresBatchAndStaysUnhealthy() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        invokePrivate(buffer, "safeProbe");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<APICallBack<HttpResponse>> captor =
                (ArgumentCaptor<APICallBack<HttpResponse>>) (ArgumentCaptor) ArgumentCaptor.forClass(APICallBack.class);
        verify(mockApi).createEventsBatchAsync(anyList(), captor.capture());

        HttpContext ctx = mock(HttpContext.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(ctx.getResponse()).thenReturn(resp);
        when(resp.getStatusCode()).thenReturn(503);
        when(resp.getRawBody()).thenReturn(null);

        captor.getValue().onSuccess(ctx, resp);

        Assert.assertFalse(buffer.isHealthy());
        Assert.assertEquals(currentCount(buffer), 2);
    }

    @Test
    public void testProbeNonRetryableStatusDiscardsBatch() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        invokePrivate(buffer, "safeProbe");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<APICallBack<HttpResponse>> captor =
                (ArgumentCaptor<APICallBack<HttpResponse>>) (ArgumentCaptor) ArgumentCaptor.forClass(APICallBack.class);
        verify(mockApi).createEventsBatchAsync(anyList(), captor.capture());

        HttpContext ctx = mock(HttpContext.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(ctx.getResponse()).thenReturn(resp);
        when(resp.getStatusCode()).thenReturn(400);
        when(resp.getRawBody()).thenReturn(null);

        captor.getValue().onSuccess(ctx, resp);

        Assert.assertEquals(currentCount(buffer), 0);
    }

    @Test
    public void testProbeOnFailureCallbackRestoresBatch() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        buffer.onRetryableFailure(events(2));
        invokePrivate(buffer, "safeProbe");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<APICallBack<HttpResponse>> captor =
                (ArgumentCaptor<APICallBack<HttpResponse>>) (ArgumentCaptor) ArgumentCaptor.forClass(APICallBack.class);
        verify(mockApi).createEventsBatchAsync(anyList(), captor.capture());

        captor.getValue().onFailure(mock(HttpContext.class), new RuntimeException("network down"));

        Assert.assertFalse(buffer.isHealthy());
        Assert.assertEquals(currentCount(buffer), 2);
    }

    @Test
    public void testProbeNoOpWhenHealthyAndEmpty() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100);
        invokePrivate(buffer, "safeProbe");
        verify(mockApi, org.mockito.Mockito.never()).createEventsBatchAsync(anyList(), any());
    }

    @Test
    public void testKeyTailShortKeyMasked() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100, "abc");
        Assert.assertEquals(invokeKeyTail(buffer), "****");
    }

    @Test
    public void testKeyTailLongKeyTrimmed() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100, "very-long-moesif-key-xyzw");
        Assert.assertEquals(invokeKeyTail(buffer), "xyzw");
    }

    @Test
    public void testKeyTailNullKeyMasked() throws Exception {
        MoesifRetryBuffer buffer = buildBuffer(100, null);
        Assert.assertEquals(invokeKeyTail(buffer), "****");
    }

    // -------- reflection helpers --------

    private static int currentCount(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("currentEventCount");
        f.setAccessible(true);
        return f.getInt(buffer);
    }

    private static Deque<?> stash(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("stash");
        f.setAccessible(true);
        return (Deque<?>) f.get(buffer);
    }

    private static long droppedCount(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("droppedDueToFull");
        f.setAccessible(true);
        return ((AtomicLong) f.get(buffer)).get();
    }

    @SuppressWarnings("unused")
    private static AtomicBoolean healthyFlag(MoesifRetryBuffer buffer) throws Exception {
        Field f = MoesifRetryBuffer.class.getDeclaredField("healthy");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(buffer);
    }

    private static void invokePrivate(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static String invokeKeyTail(MoesifRetryBuffer buffer) throws Exception {
        Method m = MoesifRetryBuffer.class.getDeclaredMethod("keyTail");
        m.setAccessible(true);
        return (String) m.invoke(buffer);
    }
}
