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

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.properties.MoesifEventData;
import org.wso2.am.analytics.publisher.properties.OrgMoesifKeyMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test case for OrgMoesifKeyMapping and MoesifEventData.
 */
public class OrgMoesifKeyMappingTestCase {

    @Test
    public void testDefaultConstructor() throws Exception {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping();
        Assert.assertNull(mapping.getOrganizationId(), "Organization ID should be null");
        Assert.assertFalse(mapping.hasKeys(), "Should have no keys initially");
        Assert.assertEquals(mapping.getEnvironmentKeyMap().size(), 0, "Environment map should be empty");
        Assert.assertEquals(mapping.getEnvironmentEventBatches().size(), 0, "Event batches should be empty");
    }

    @Test
    public void testConstructorWithOrgId() throws Exception {
        String orgId = "test-org-123";
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping(orgId);
        Assert.assertEquals(mapping.getOrganizationId(), orgId, "Organization ID should match");
        Assert.assertFalse(mapping.hasKeys(), "Should have no keys initially");
    }

    @Test
    public void testConstructorWithEnvironmentMap() {
        String orgId = "test-org-456";
        Map<String, String> envMap = new HashMap<>();
        envMap.put("Production", "prod-key-123");
        envMap.put("Development", "dev-key-456");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping(orgId, envMap);
        
        Assert.assertEquals(mapping.getOrganizationId(), orgId, "Organization ID should match");
        Assert.assertTrue(mapping.hasKeys(), "Should have keys");
        Assert.assertEquals(mapping.getEnvironmentKeyMap().size(), 2, "Should have 2 environments");
    }

    @Test
    public void testCaseInsensitiveEnvironmentLookup() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("Production", "prod-key");
        envMap.put("Development", "dev-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", envMap);

        // Test case-insensitive lookup
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("Production"), "prod-key");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("production"), "prod-key");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("PRODUCTION"), "prod-key");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("Development"), "dev-key");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("development"), "dev-key");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("DEVELOPMENT"), "dev-key");
    }

    @Test
    public void testGetMoesifKeyForNullEnvironment() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", envMap);
        Assert.assertNull(mapping.getMoesifKeyForEnvironment(null), "Should return null for null environment");
    }

    @Test
    public void testGetMoesifKeyForNonExistentEnvironment() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", envMap);
        Assert.assertNull(mapping.getMoesifKeyForEnvironment("Staging"), "Should return null for non-existent environment");
    }

    @Test
    public void testHasSingleEnvironment() {
        Map<String, String> singleEnv = new HashMap<>();
        singleEnv.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", singleEnv);
        Assert.assertTrue(mapping.hasSingleEnvironment(), "Should have single environment");

        Map<String, String> multiEnv = new HashMap<>();
        multiEnv.put("Production", "prod-key");
        multiEnv.put("Development", "dev-key");

        OrgMoesifKeyMapping mapping2 = new OrgMoesifKeyMapping("org-2", multiEnv);
        Assert.assertFalse(mapping2.hasSingleEnvironment(), "Should not have single environment");
    }

    @Test
    public void testGetSingleEnvironmentKey() {
        Map<String, String> singleEnv = new HashMap<>();
        singleEnv.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", singleEnv);
        Assert.assertEquals(mapping.getSingleEnvironmentKey(), "prod-key", "Should return single key");

        Map<String, String> multiEnv = new HashMap<>();
        multiEnv.put("Production", "prod-key");
        multiEnv.put("Development", "dev-key");

        OrgMoesifKeyMapping mapping2 = new OrgMoesifKeyMapping("org-2", multiEnv);
        Assert.assertNull(mapping2.getSingleEnvironmentKey(), "Should return null for multiple environments");
    }

    @Test
    public void testAddEventWithMoesifEventData() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventId", "event-123");
        MoesifEventData eventData = new MoesifEventData(eventMap);

        mapping.addEvent("Production", eventData);

        List<MoesifEventData> events = mapping.getEventsForEnvironment("Production");
        Assert.assertEquals(events.size(), 1, "Should have 1 event");
        Assert.assertEquals(events.get(0).getEventData().get("eventId"), "event-123");
    }

    @Test
    public void testAddEventWithMap() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventId", "event-456");

        mapping.addEvent("Development", eventMap);

        List<MoesifEventData> events = mapping.getEventsForEnvironment("Development");
        Assert.assertEquals(events.size(), 1, "Should have 1 event");
    }

    @Test
    public void testAddEventCaseInsensitive() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        Map<String, Object> event1 = new HashMap<>();
        event1.put("id", "1");
        Map<String, Object> event2 = new HashMap<>();
        event2.put("id", "2");
        Map<String, Object> event3 = new HashMap<>();
        event3.put("id", "3");

        // Add events with different cases
        mapping.addEvent("Production", event1);
        mapping.addEvent("production", event2);
        mapping.addEvent("PRODUCTION", event3);

        // All should be in the same list
        List<MoesifEventData> events = mapping.getEventsForEnvironment("production");
        Assert.assertEquals(events.size(), 3, "All 3 events should be in same environment");
    }

    @Test
    public void testAddEventWithNullEnvironment() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventId", "event-null");

        mapping.addEvent(null, eventMap);

        // Should not throw exception and should not add event
        Assert.assertEquals(mapping.getEnvironmentEventBatches().size(), 0, "Should have no events");
    }

    @Test
    public void testAddEventWithNullEventData() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        mapping.addEvent("Production", (MoesifEventData) null);

        // Should not throw exception and should not add event
        Assert.assertEquals(mapping.getEnvironmentEventBatches().size(), 0, "Should have no events");
    }

    @Test
    public void testGetEventsForNullEnvironment() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        List<MoesifEventData> events = mapping.getEventsForEnvironment(null);
        Assert.assertNotNull(events, "Should return non-null list");
        Assert.assertEquals(events.size(), 0, "Should return empty list");
    }

    @Test
    public void testGetEventsForNonExistentEnvironment() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        List<MoesifEventData> events = mapping.getEventsForEnvironment("NonExistent");
        Assert.assertNotNull(events, "Should return non-null list");
        Assert.assertEquals(events.size(), 0, "Should return empty list");
    }

    @Test
    public void testGetEnvironments() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        
        mapping.addEvent("Production", new HashMap<>());
        mapping.addEvent("Development", new HashMap<>());
        mapping.addEvent("Staging", new HashMap<>());

        Assert.assertEquals(mapping.getEnvironments().size(), 3, "Should have 3 environments");
        Assert.assertTrue(mapping.getEnvironments().contains("production"), "Should contain production");
        Assert.assertTrue(mapping.getEnvironments().contains("development"), "Should contain development");
        Assert.assertTrue(mapping.getEnvironments().contains("staging"), "Should contain staging");
    }

    @Test
    public void testConstructorWithNullEnvironmentMap() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", null);
        
        Assert.assertFalse(mapping.hasKeys(), "Should have no keys");
        Assert.assertEquals(mapping.getEnvironmentKeyMap().size(), 0, "Environment map should be empty");
    }

    @Test
    public void testConstructorWithNullKeysInMap() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put(null, "null-key");
        envMap.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", envMap);
        
        // Null keys should be skipped
        Assert.assertEquals(mapping.getEnvironmentKeyMap().size(), 1, "Should have 1 environment");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("Production"), "prod-key");
    }

    @Test
    public void testGetEnvironmentKeyMapIsUnmodifiable() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("Production", "prod-key");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1", envMap);
        
        Map<String, String> retrieved = mapping.getEnvironmentKeyMap();
        
        try {
            retrieved.put("Staging", "staging-key");
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testGetEnvironmentEventBatchesIsUnmodifiable() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        mapping.addEvent("Production", new HashMap<>());
        
        Map<String, List<MoesifEventData>> retrieved = mapping.getEnvironmentEventBatches();
        
        try {
            retrieved.put("Staging", null);
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testGetEventsForEnvironmentIsUnmodifiable() {
        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("org-1");
        mapping.addEvent("Production", new HashMap<>());
        
        List<MoesifEventData> events = mapping.getEventsForEnvironment("Production");
        
        try {
            events.add(new MoesifEventData(new HashMap<>()));
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testMoesifEventDataConstructor() {
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("key1", "value1");
        eventMap.put("key2", 123);

        MoesifEventData eventData = new MoesifEventData(eventMap);
        
        Assert.assertNotNull(eventData.getEventData(), "Event data should not be null");
        Assert.assertEquals(eventData.getEventData().get("key1"), "value1");
        Assert.assertEquals(eventData.getEventData().get("key2"), 123);
    }

    @Test
    public void testRealWorldScenario() {
        // Simulate real-world scenario with service payload
        Map<String, String> serviceResponse = new HashMap<>();
        serviceResponse.put("Production", "eyJ0eXAi...Tu4");
        serviceResponse.put("Development", "eyJ0eXAi...nA");

        OrgMoesifKeyMapping mapping = new OrgMoesifKeyMapping("df4806d1-6b29-46f5-896d-d1c5083bd4f0", serviceResponse);

        // Add events with different casing
        Map<String, Object> event1 = new HashMap<>();
        event1.put("eventId", "evt-1");
        event1.put("deployment-type", "Production");
        
        Map<String, Object> event2 = new HashMap<>();
        event2.put("eventId", "evt-2");
        event2.put("deployment-type", "production");
        
        Map<String, Object> event3 = new HashMap<>();
        event3.put("eventId", "evt-3");
        event3.put("deployment-type", "Development");

        mapping.addEvent("Production", event1);
        mapping.addEvent("production", event2);
        mapping.addEvent("Development", event3);

        // Verify
        Assert.assertEquals(mapping.getEventsForEnvironment("production").size(), 2);
        Assert.assertEquals(mapping.getEventsForEnvironment("development").size(), 1);
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("PRODUCTION"), "eyJ0eXAi...Tu4");
        Assert.assertEquals(mapping.getMoesifKeyForEnvironment("development"), "eyJ0eXAi...nA");
    }
}
