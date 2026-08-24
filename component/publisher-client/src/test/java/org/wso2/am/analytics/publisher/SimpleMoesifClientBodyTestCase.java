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

import com.moesif.api.BodyParser;
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
import org.wso2.am.analytics.publisher.util.Constants;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the request/response body support in {@link SimpleMoesifClient}: the {@code extractAndRemoveBody},
 * {@code applyBodyContentType} and {@code resolveBody} helpers, and the {@code .body(...)} /
 * {@code .transferEncoding(...)} wiring in both branches of {@code buildEventResponse}.
 *
 * <p>The gateway captures a body into the event's nested {@code properties} map; this client maps it onto the
 * Moesif SDK event. Bodies must reach Moesif's Body panels (JSON as a structured object, everything else as
 * Base64) and must never be duplicated into Moesif metadata.</p>
 *
 * <p>Body bodies used here are deliberately ASCII: Moesif's Base64 fallback encodes with the platform default
 * charset, so a multibyte body would make golden values charset-dependent.</p>
 */
public class SimpleMoesifClientBodyTestCase {

    private static final String JSON_OBJECT_BODY = "{\"a\":1}";
    private static final String TEXT_BODY = "hello world";
    private static final String PRE_ENCODED_BODY = "SGVsbG8=";
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final String XML_CONTENT_TYPE = "application/xml";

    private MockedConstruction<MoesifAPIClient> mockedClients;

    @BeforeMethod
    public void setUp() {
        APIController mockApi = mock(APIController.class);
        mockedClients = Mockito.mockConstruction(MoesifAPIClient.class,
                (mock, ctx) -> when(mock.getAPI()).thenReturn(mockApi));
    }

    @AfterMethod
    public void tearDown() {
        if (mockedClients != null) {
            mockedClients.close();
        }
    }

    // ----- extractAndRemoveBody -----

    @Test
    public void testExtractAndRemoveBodyReturnsNullWhenPropertiesAbsent() throws Exception {
        Map<String, Object> data = new HashMap<>();
        Assert.assertNull(extractAndRemoveBody(newClient(), data, Constants.REQUEST_BODY));
    }

    @Test
    public void testExtractAndRemoveBodyReturnsNullWhenPropertiesNotAMap() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.PROPERTIES, "not-a-map");
        Assert.assertNull(extractAndRemoveBody(newClient(), data, Constants.REQUEST_BODY));
    }

    @Test
    public void testExtractAndRemoveBodyReturnsNullWhenKeyAbsent() throws Exception {
        Map<String, Object> data = dataWithProperties(new LinkedHashMap<>());
        Assert.assertNull(extractAndRemoveBody(newClient(), data, Constants.REQUEST_BODY));
    }

    @Test
    public void testExtractAndRemoveBodyReturnsValueAndRemovesKey() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, JSON_OBJECT_BODY);
        Map<String, Object> data = dataWithProperties(props);

        Assert.assertEquals(extractAndRemoveBody(newClient(), data, Constants.REQUEST_BODY), JSON_OBJECT_BODY);
        Assert.assertFalse(props.containsKey(Constants.REQUEST_BODY), "body key must be removed from properties");
    }

    @Test
    public void testExtractAndRemoveBodyRemovesNonStringValue() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, 42);
        Map<String, Object> data = dataWithProperties(props);

        Assert.assertEquals(extractAndRemoveBody(newClient(), data, Constants.REQUEST_BODY), 42);
        Assert.assertFalse(props.containsKey(Constants.REQUEST_BODY));
    }

    // ----- applyBodyContentType -----

    @Test
    public void testApplyBodyContentTypeNoOpWhenPropertiesAbsent() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), new HashMap<>(), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenPropertiesNotAMap() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.PROPERTIES, "not-a-map");
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), data, headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenBodyAbsent() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_CONTENT_TYPE, XML_CONTENT_TYPE);
        Map<String, String> headers = jsonHeaders();

        applyBodyContentType(newClient(), dataWithProperties(props), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenBodyNotAString() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType(42, XML_CONTENT_TYPE), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenBodyEmpty() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType("", XML_CONTENT_TYPE), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenContentTypeAbsent() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, TEXT_BODY);
        Map<String, String> headers = jsonHeaders();

        applyBodyContentType(newClient(), dataWithProperties(props), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenContentTypeNotAString() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType(TEXT_BODY, 42), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeNoOpWhenContentTypeHasNoSlash() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType(TEXT_BODY, "json"), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testApplyBodyContentTypeOverridesContentTypeHeader() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType(TEXT_BODY, XML_CONTENT_TYPE), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), XML_CONTENT_TYPE);
    }

    @Test
    public void testApplyBodyContentTypeTrimsContentType() throws Exception {
        Map<String, String> headers = jsonHeaders();
        applyBodyContentType(newClient(), dataWithBodyAndContentType(TEXT_BODY, "  text/plain  "), headers);
        Assert.assertEquals(headers.get(Constants.MOESIF_CONTENT_TYPE_KEY), TEXT_CONTENT_TYPE);
    }

    @Test
    public void testApplyBodyContentTypeLeavesContentTypePropertyInPlace() throws Exception {
        Map<String, Object> data = dataWithBodyAndContentType(TEXT_BODY, XML_CONTENT_TYPE);
        applyBodyContentType(newClient(), data, jsonHeaders());

        Assert.assertEquals(propertiesOf(data).get(Constants.REQUEST_CONTENT_TYPE), XML_CONTENT_TYPE,
                "the content-type property is metadata and must not be consumed");
    }

    @Test
    public void testApplyBodyContentTypeRequestAndResponseAreIndependent() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, TEXT_BODY);
        props.put(Constants.REQUEST_CONTENT_TYPE, XML_CONTENT_TYPE);
        Map<String, Object> data = dataWithProperties(props);

        Map<String, String> reqHeaders = jsonHeaders();
        Map<String, String> rspHeaders = jsonHeaders();
        applyBodyContentType(newClient(), data, reqHeaders, Constants.REQUEST_BODY, Constants.REQUEST_CONTENT_TYPE);
        applyBodyContentType(newClient(), data, rspHeaders, Constants.RESPONSE_BODY, Constants.RESPONSE_CONTENT_TYPE);

        Assert.assertEquals(reqHeaders.get(Constants.MOESIF_CONTENT_TYPE_KEY), XML_CONTENT_TYPE);
        Assert.assertEquals(rspHeaders.get(Constants.MOESIF_CONTENT_TYPE_KEY), Constants.MOESIF_CONTENT_TYPE_HEADER,
                "a request-side content type must not affect response headers");
    }

    // ----- resolveBody -----

    @Test
    public void testResolveBodyReturnsEmptyWrapperWhenNoBody() throws Exception {
        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), dataWithProperties(new LinkedHashMap<>()),
                jsonHeaders());

        Assert.assertNull(wrapper.body);
        Assert.assertNull(wrapper.transferEncoding);
    }

    @Test
    public void testResolveBodyRemovesEncodingEvenWhenNoBody() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);
        Map<String, Object> data = dataWithProperties(props);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertNull(wrapper.body);
        Assert.assertNull(wrapper.transferEncoding);
        Assert.assertFalse(props.containsKey(Constants.REQUEST_BODY_TRANSFER_ENCODING),
                "a stray encoding hint must be stripped so it cannot leak into metadata");
    }

    @Test
    public void testResolveBodyReturnsEmptyWrapperButRemovesKeyWhenBodyNotAString() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, 42);
        Map<String, Object> data = dataWithProperties(props);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertNull(wrapper.body, "a non-String body is not publishable");
        Assert.assertFalse(props.containsKey(Constants.REQUEST_BODY), "yet it must not leak into metadata either");
    }

    @Test
    public void testResolveBodyReturnsEmptyWrapperWhenBodyEmpty() throws Exception {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, "");
        Map<String, Object> data = dataWithProperties(props);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertNull(wrapper.body);
        Assert.assertNull(wrapper.transferEncoding);
        Assert.assertFalse(props.containsKey(Constants.REQUEST_BODY));
    }

    @Test
    public void testResolveBodyPassesThroughBase64Verbatim() throws Exception {
        Map<String, Object> data = dataWithBodyAndEncoding(PRE_ENCODED_BODY, Constants.TRANSFER_ENCODING_BASE64);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertEquals(wrapper.body, PRE_ENCODED_BODY, "gateway-encoded binary must not be re-encoded");
        Assert.assertEquals(wrapper.transferEncoding, Constants.TRANSFER_ENCODING_BASE64);
    }

    @Test
    public void testResolveBodyRemovesBothKeysFromProperties() throws Exception {
        Map<String, Object> data = dataWithBodyAndEncoding(PRE_ENCODED_BODY, Constants.TRANSFER_ENCODING_BASE64);

        resolveBody(newClient(), data, jsonHeaders());

        Assert.assertFalse(propertiesOf(data).containsKey(Constants.REQUEST_BODY));
        Assert.assertFalse(propertiesOf(data).containsKey(Constants.REQUEST_BODY_TRANSFER_ENCODING));
    }

    @Test
    public void testResolveBodyIgnoresWrongCaseEncoding() throws Exception {
        Map<String, Object> data = dataWithBodyAndEncoding(TEXT_BODY, "BASE64");

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, textHeaders());

        Assert.assertNotEquals(wrapper.body, TEXT_BODY, "the encoding hint match is case-sensitive");
        Assert.assertEquals(wrapper.transferEncoding, Constants.TRANSFER_ENCODING_BASE64);
        Assert.assertEquals(decodeBase64(wrapper.body), TEXT_BODY);
    }

    @Test
    public void testResolveBodyIgnoresNonStringEncoding() throws Exception {
        Map<String, Object> data = dataWithBodyAndEncoding(TEXT_BODY, Boolean.TRUE);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, textHeaders());

        Assert.assertEquals(decodeBase64(wrapper.body), TEXT_BODY);
        Assert.assertEquals(wrapper.transferEncoding, Constants.TRANSFER_ENCODING_BASE64);
    }

    @Test
    public void testResolveBodyParsesJsonObject() throws Exception {
        Map<String, Object> data = dataWithBody(JSON_OBJECT_BODY);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertTrue(wrapper.body instanceof Map, "JSON must reach Moesif as a structured object");
        Assert.assertEquals(((Map<?, ?>) wrapper.body).get("a"), 1);
        Assert.assertNull(wrapper.transferEncoding, "JSON carries no transfer encoding");
    }

    @Test
    public void testResolveBodyParsesJsonArray() throws Exception {
        Map<String, Object> data = dataWithBody("[1,2]");

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertTrue(wrapper.body instanceof List);
        Assert.assertEquals(((List<?>) wrapper.body).size(), 2);
        Assert.assertNull(wrapper.transferEncoding);
    }

    @Test
    public void testResolveBodyBase64EncodesNonJson() throws Exception {
        Map<String, Object> data = dataWithBody(TEXT_BODY);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, textHeaders());

        Assert.assertEquals(wrapper.transferEncoding, Constants.TRANSFER_ENCODING_BASE64);
        Assert.assertEquals(decodeBase64(wrapper.body), TEXT_BODY);
    }

    @Test
    public void testResolveBodyFallsBackToBase64OnMalformedJson() throws Exception {
        Map<String, Object> data = dataWithBody("{not json");

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, jsonHeaders());

        Assert.assertEquals(wrapper.transferEncoding, Constants.TRANSFER_ENCODING_BASE64);
        Assert.assertEquals(decodeBase64(wrapper.body), "{not json");
    }

    @Test
    public void testResolveBodyDetectsJsonByContentPrefixDespiteContentType() throws Exception {
        Map<String, Object> data = dataWithBody(JSON_OBJECT_BODY);

        BodyParser.BodyWrapper wrapper = resolveBody(newClient(), data, textHeaders());

        Assert.assertTrue(wrapper.body instanceof Map, "a JSON-shaped body is parsed even under text/plain");
        Assert.assertNull(wrapper.transferEncoding);
    }

    // ----- ordering: applyBodyContentType must run before resolveBody -----

    @Test
    public void testContentTypeOverrideIsAppliedBeforeBodyResolution() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, "123");
        propertiesOf(data).put(Constants.REQUEST_CONTENT_TYPE, TEXT_CONTENT_TYPE);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getRequest().getTransferEncoding(), Constants.TRANSFER_ENCODING_BASE64,
                "the text/plain override must reach BodyParser, otherwise 123 would parse as JSON");
        Assert.assertEquals(decodeBase64(ev.getRequest().getBody()), "123");
    }

    @Test
    public void testNumericBodyParsesAsJsonWithoutContentTypeOverride() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, "123");

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getRequest().getBody(), 123, "the default JSON header makes 123 a number");
        Assert.assertNull(ev.getRequest().getTransferEncoding());
    }

    // ----- buildEventResponse: non-error branch -----

    @Test
    public void testBuildEventResponseIncludesRequestBody() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, JSON_OBJECT_BODY);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertTrue(ev.getRequest().getBody() instanceof Map);
        Assert.assertEquals(((Map<?, ?>) ev.getRequest().getBody()).get("a"), 1);
    }

    @Test
    public void testBuildEventResponseIncludesResponseBody() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.RESPONSE_BODY, JSON_OBJECT_BODY);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertTrue(ev.getResponse().getBody() instanceof Map);
        Assert.assertEquals(((Map<?, ?>) ev.getResponse().getBody()).get("a"), 1);
    }

    @Test
    public void testBuildEventResponseRequestAndResponseBodiesAreIndependent() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, JSON_OBJECT_BODY);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertNotNull(ev.getRequest().getBody());
        Assert.assertNull(ev.getResponse().getBody(), "only the request body was captured");
    }

    @Test
    public void testBuildEventResponseCarriesBase64TransferEncoding() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, PRE_ENCODED_BODY);
        propertiesOf(data).put(Constants.REQUEST_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);
        propertiesOf(data).put(Constants.RESPONSE_BODY, PRE_ENCODED_BODY);
        propertiesOf(data).put(Constants.RESPONSE_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getRequest().getBody(), PRE_ENCODED_BODY);
        Assert.assertEquals(ev.getRequest().getTransferEncoding(), Constants.TRANSFER_ENCODING_BASE64);
        Assert.assertEquals(ev.getResponse().getBody(), PRE_ENCODED_BODY);
        Assert.assertEquals(ev.getResponse().getTransferEncoding(), Constants.TRANSFER_ENCODING_BASE64);
    }

    @Test
    public void testBuildEventResponseWithoutBodyKeysLeavesBodiesNull() throws Exception {
        EventModel ev = newClient().buildEventResponse(validResponseData());

        Assert.assertNull(ev.getRequest().getBody(), "events without captured bodies must be unchanged");
        Assert.assertNull(ev.getRequest().getTransferEncoding());
        Assert.assertNull(ev.getResponse().getBody());
        Assert.assertNull(ev.getResponse().getTransferEncoding());
    }

    @Test
    public void testBuildEventResponseOverridesContentTypeHeaderWhenBodyPresent() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, TEXT_BODY);
        propertiesOf(data).put(Constants.REQUEST_CONTENT_TYPE, TEXT_CONTENT_TYPE);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getRequest().getHeaders().get(Constants.MOESIF_CONTENT_TYPE_KEY), TEXT_CONTENT_TYPE);
    }

    @Test
    public void testBuildEventResponseKeepsDefaultContentTypeHeaderWithoutBody() throws Exception {
        EventModel ev = newClient().buildEventResponse(validResponseData());

        Assert.assertEquals(ev.getRequest().getHeaders().get(Constants.MOESIF_CONTENT_TYPE_KEY),
                Constants.MOESIF_CONTENT_TYPE_HEADER);
    }

    @Test
    public void testBuildEventResponseOverridesGatewaySuppliedContentTypeHeader() throws Exception {
        Map<String, Object> data = validResponseData();
        Map<String, String> gatewayHeaders = new HashMap<>();
        gatewayHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, "application/octet-stream");
        propertiesOf(data).put(Constants.REQUEST_HEADERS, gatewayHeaders);
        propertiesOf(data).put(Constants.REQUEST_BODY, TEXT_BODY);
        propertiesOf(data).put(Constants.REQUEST_CONTENT_TYPE, XML_CONTENT_TYPE);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getRequest().getHeaders().get(Constants.MOESIF_CONTENT_TYPE_KEY), XML_CONTENT_TYPE,
                "the captured body's own media type wins over a forwarded header");
    }

    // ----- buildEventResponse: error/fault branch -----

    @Test
    public void testBuildEventResponseErrorBranchIncludesRequestBody() throws Exception {
        Map<String, Object> data = validErrorData();
        propertiesOf(data).put(Constants.REQUEST_BODY, JSON_OBJECT_BODY);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertTrue(ev.getRequest().getBody() instanceof Map);
        Assert.assertEquals(((Map<?, ?>) ev.getRequest().getBody()).get("a"), 1);
    }

    @Test
    public void testBuildEventResponseErrorBranchIncludesResponseBody() throws Exception {
        Map<String, Object> data = validErrorData();
        propertiesOf(data).put(Constants.RESPONSE_BODY, PRE_ENCODED_BODY);
        propertiesOf(data).put(Constants.RESPONSE_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(ev.getResponse().getBody(), PRE_ENCODED_BODY);
        Assert.assertEquals(ev.getResponse().getTransferEncoding(), Constants.TRANSFER_ENCODING_BASE64);
    }

    // ----- bodies must never leak into Moesif metadata -----

    @Test
    public void testBuildEventResponseDoesNotLeakBodyKeysIntoMetadata() throws Exception {
        Map<String, Object> data = validResponseData();
        putAllBodyKeys(propertiesOf(data));

        EventModel ev = newClient().buildEventResponse(data);

        assertNoBodyKeysInMetadata(ev);
    }

    @Test
    public void testBuildEventResponseErrorBranchDoesNotLeakBodyKeysIntoMetadata() throws Exception {
        Map<String, Object> data = validErrorData();
        putAllBodyKeys(propertiesOf(data));

        EventModel ev = newClient().buildEventResponse(data);

        assertNoBodyKeysInMetadata(ev);
    }

    @Test
    public void testBuildEventResponseDoesNotLeakBodyKeysAlongsideAiMetadata() throws Exception {
        Map<String, Object> data = validResponseData();
        putAllBodyKeys(propertiesOf(data));
        propertiesOf(data).put(Constants.AI_METADATA, "ai-md");
        propertiesOf(data).put(Constants.MCP_ANALYTICS, "mcp");

        EventModel ev = newClient().buildEventResponse(data);

        assertNoBodyKeysInMetadata(ev);
        Assert.assertEquals(metadataOf(ev).get(Constants.AI_METADATA), "ai-md");
        Assert.assertEquals(metadataOf(ev).get(Constants.MCP_ANALYTICS), "mcp");
    }

    @Test
    public void testBuildEventResponseRetainsContentTypePropertiesInMetadata() throws Exception {
        Map<String, Object> data = validResponseData();
        propertiesOf(data).put(Constants.REQUEST_BODY, TEXT_BODY);
        propertiesOf(data).put(Constants.REQUEST_CONTENT_TYPE, TEXT_CONTENT_TYPE);

        EventModel ev = newClient().buildEventResponse(data);

        Assert.assertEquals(metadataOf(ev).get(Constants.REQUEST_CONTENT_TYPE), TEXT_CONTENT_TYPE,
                "unlike bodies, the content type is useful metadata and is deliberately kept");
    }

    // ----- helpers -----

    private static SimpleMoesifClient newClient() {
        return new SimpleMoesifClient("test-key");
    }

    private static Object extractAndRemoveBody(SimpleMoesifClient client, Map<String, Object> data, String key)
            throws Exception {
        Method method = SimpleMoesifClient.class.getDeclaredMethod("extractAndRemoveBody", Map.class, String.class);
        method.setAccessible(true);
        return method.invoke(client, data, key);
    }

    private static void applyBodyContentType(SimpleMoesifClient client, Map<String, Object> data,
                                             Map<String, String> headers) throws Exception {
        applyBodyContentType(client, data, headers, Constants.REQUEST_BODY, Constants.REQUEST_CONTENT_TYPE);
    }

    private static void applyBodyContentType(SimpleMoesifClient client, Map<String, Object> data,
                                             Map<String, String> headers, String bodyKey, String contentTypeKey)
            throws Exception {
        Method method = SimpleMoesifClient.class.getDeclaredMethod("applyBodyContentType", Map.class, Map.class,
                String.class, String.class);
        method.setAccessible(true);
        method.invoke(client, data, headers, bodyKey, contentTypeKey);
    }

    private static BodyParser.BodyWrapper resolveBody(SimpleMoesifClient client, Map<String, Object> data,
                                                      Map<String, String> headers) throws Exception {
        Method method = SimpleMoesifClient.class.getDeclaredMethod("resolveBody", Map.class, Map.class,
                String.class, String.class);
        method.setAccessible(true);
        return (BodyParser.BodyWrapper) method.invoke(client, data, headers, Constants.REQUEST_BODY,
                Constants.REQUEST_BODY_TRANSFER_ENCODING);
    }

    private static Map<String, Object> dataWithProperties(LinkedHashMap<String, Object> properties) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.PROPERTIES, properties);
        return data;
    }

    private static Map<String, Object> dataWithBody(Object body) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, body);
        return dataWithProperties(props);
    }

    private static Map<String, Object> dataWithBodyAndEncoding(Object body, Object encoding) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, body);
        props.put(Constants.REQUEST_BODY_TRANSFER_ENCODING, encoding);
        return dataWithProperties(props);
    }

    private static Map<String, Object> dataWithBodyAndContentType(Object body, Object contentType) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put(Constants.REQUEST_BODY, body);
        props.put(Constants.REQUEST_CONTENT_TYPE, contentType);
        return dataWithProperties(props);
    }

    private static Map<String, String> jsonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.MOESIF_CONTENT_TYPE_KEY, Constants.MOESIF_CONTENT_TYPE_HEADER);
        return headers;
    }

    private static Map<String, String> textHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.MOESIF_CONTENT_TYPE_KEY, TEXT_CONTENT_TYPE);
        return headers;
    }

    private static void putAllBodyKeys(Map<String, Object> properties) {
        properties.put(Constants.REQUEST_BODY, JSON_OBJECT_BODY);
        properties.put(Constants.REQUEST_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);
        properties.put(Constants.RESPONSE_BODY, JSON_OBJECT_BODY);
        properties.put(Constants.RESPONSE_BODY_TRANSFER_ENCODING, Constants.TRANSFER_ENCODING_BASE64);
    }

    private static void assertNoBodyKeysInMetadata(EventModel event) {
        Map<String, Object> metadata = metadataOf(event);
        Assert.assertFalse(metadata.containsKey(Constants.REQUEST_BODY), "request body leaked into metadata");
        Assert.assertFalse(metadata.containsKey(Constants.RESPONSE_BODY), "response body leaked into metadata");
        Assert.assertFalse(metadata.containsKey(Constants.REQUEST_BODY_TRANSFER_ENCODING),
                "request transfer encoding leaked into metadata");
        Assert.assertFalse(metadata.containsKey(Constants.RESPONSE_BODY_TRANSFER_ENCODING),
                "response transfer encoding leaked into metadata");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataOf(EventModel event) {
        Assert.assertNotNull(event.getMetadata());
        return (Map<String, Object>) event.getMetadata();
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> propertiesOf(Map<String, Object> data) {
        return (LinkedHashMap<String, Object>) data.get(Constants.PROPERTIES);
    }

    /**
     * Decodes a Base64 body produced by Moesif's {@code BodyParser}. Its encoder line-wraps at 76 characters and
     * appends a newline, so a MIME decoder is used rather than comparing against a plain Base64 golden value.
     */
    private static String decodeBase64(Object body) {
        Assert.assertTrue(body instanceof String, "expected a Base64-encoded String body");
        return new String(Base64.getMimeDecoder().decode((String) body), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> validResponseData() {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.USER_IP, "127.0.0.1");
        data.put(Constants.API_RESOURCE_TEMPLATE, "/{value}");
        data.put(Constants.RESPONSE_LATENCY, 2000L);
        data.put(Constants.REQUEST_TIMESTAMP, Instant.now().toString());
        data.put(Constants.API_METHOD, "POST");
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
}
