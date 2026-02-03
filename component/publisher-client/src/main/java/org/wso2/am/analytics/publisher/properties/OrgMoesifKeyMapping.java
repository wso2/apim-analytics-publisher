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
package org.wso2.am.analytics.publisher.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the Moesif key mapping for an organization.
 * Maps environment names to their corresponding Moesif API keys.
 * Also supports grouping events by environment for batch processing.
 * Thread-safe for concurrent access.
 */
public class OrgMoesifKeyMapping {
    private volatile String organizationId;
    private final Map<String, String> environmentKeyMap;
    private final Map<String, List<MoesifEventData>> environmentEventBatches;

    /**
     * Default constructor.
     */
    public OrgMoesifKeyMapping() {
        this.environmentKeyMap = new ConcurrentHashMap<>();
        this.environmentEventBatches = new ConcurrentHashMap<>();
    }

    /**
     * Constructor with organization ID.
     *
     * @param organizationId The organization ID.
     */
    public OrgMoesifKeyMapping(String organizationId) {
        this.organizationId = organizationId;
        this.environmentKeyMap = new ConcurrentHashMap<>();
        this.environmentEventBatches = new ConcurrentHashMap<>();
    }

    /**
     * Constructor with organization ID and environment key map.
     *
     * @param organizationId      The organization ID.
     * @param environmentKeyMap   Map of environment to Moesif key.
     */
    public OrgMoesifKeyMapping(String organizationId, Map<String, String> environmentKeyMap) {
        this.organizationId = organizationId;
        this.environmentKeyMap = new ConcurrentHashMap<>();
        if (environmentKeyMap != null) {
            for (Map.Entry<String, String> entry : environmentKeyMap.entrySet()) {
                String key = entry.getKey();
                if (key != null) {
                    this.environmentKeyMap.put(key.toLowerCase(), entry.getValue());
                }
            }
        }
        this.environmentEventBatches = new ConcurrentHashMap<>();
    }

    /**
     * Gets the organization ID.
     *
     * @return The organization ID.
     */
    public String getOrganizationId() {
        return organizationId;
    }

    /**
     * Gets the environment to Moesif key mapping.
     *
     * @return Map of environment names to Moesif API keys.
     */
    public Map<String, String> getEnvironmentKeyMap() {
        return Collections.unmodifiableMap(environmentKeyMap);
    }

    /**
     * Gets the Moesif key for a specific environment.
     * Environment name comparison is case-insensitive.
     *
     * @param environment The environment name.
     * @return The Moesif API key for the environment, or null if not found.
     */
    public String getMoesifKeyForEnvironment(String environment) {
        if (environment == null) {
            return null;
        }
        return environmentKeyMap.get(environment.toLowerCase());
    }

    /**
     * Checks if the organization has a Moesif key configured.
     *
     * @return true if at least one environment key is configured, false otherwise.
     */
    public boolean hasKeys() {
        return !environmentKeyMap.isEmpty();
    }

    /**
     * Checks if the organization has exactly one environment configured.
     *
     * @return true if only one environment key exists, false otherwise.
     */
    public boolean hasSingleEnvironment() {
        return environmentKeyMap.size() == 1;
    }

    /**
     * Gets the single Moesif key when only one environment exists.
     *
     * @return The Moesif API key if only one exists, null otherwise.
     */
    public String getSingleEnvironmentKey() {
        if (hasSingleEnvironment()) {
            for (String value : environmentKeyMap.values()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Adds an event to the batch for a specific environment.
     * Environment name is normalized to lowercase for case-insensitive comparison.
     *
     * @param environment The environment name.
     * @param eventData   The event data.
     */
    public void addEvent(String environment, MoesifEventData eventData) {
        if (environment != null && eventData != null) {
            environmentEventBatches
                .computeIfAbsent(environment.toLowerCase(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(eventData);
        }
    }

    /**
     * Adds an event to the batch for a specific environment.
     *
     * @param environment The environment name.
     * @param eventMap    The event data map.
     */
    public void addEvent(String environment, Map<String, Object> eventMap) {
        if (eventMap == null) {
            return;
        }
        addEvent(environment, new MoesifEventData(eventMap));
    }

    /**
     * Gets all events for a specific environment.
     * Environment name comparison is case-insensitive.
     *
     * @param environment The environment name.
     * @return List of events for the environment, or empty list if none exist.
     */
    public List<MoesifEventData> getEventsForEnvironment(String environment) {
        if (environment == null) {
            return Collections.emptyList();
        }
        List<MoesifEventData> events = environmentEventBatches.get(environment.toLowerCase());
        return events == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Gets all environment event batches.
     *
     * @return Map of environment to list of events.
     */
    public Map<String, List<MoesifEventData>> getEnvironmentEventBatches() {
        return Collections.unmodifiableMap(environmentEventBatches);
    }

    /**
     * Sets the environment event batches.
     *
     * @param environmentEventBatches Map of environment to list of events.
     */
    public void setEnvironmentEventBatches(Map<String, List<MoesifEventData>> environmentEventBatches) {
        this.environmentEventBatches.clear();
        Map<String, List<MoesifEventData>> normalized = new ConcurrentHashMap<>();
        if (environmentEventBatches != null) {
            for (Map.Entry<String, List<MoesifEventData>> entry : environmentEventBatches.entrySet()) {
                String key = entry.getKey();
                List<MoesifEventData> value = entry.getValue();
                if (key != null && value != null) {
                    normalized.put(key.toLowerCase(), new java.util.concurrent.CopyOnWriteArrayList<>(value));
                }
            }
        }
        this.environmentEventBatches.clear();
        this.environmentEventBatches.putAll(normalized);
    }

    /**
     * Gets all environment names that have events.
     *
     * @return Set of environment names (normalized to lowercase).
     */
    public java.util.Set<String> getEnvironments() {
        return new java.util.HashSet<>(environmentEventBatches.keySet());
    }
}
