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

import java.util.Collections;
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

    /**
     * Default constructor.
     */
    public OrgMoesifKeyMapping() {
        this.environmentKeyMap = new ConcurrentHashMap<>();
    }

    /**
     * Constructor with organization ID.
     *
     * @param organizationId The organization ID.
     */
    public OrgMoesifKeyMapping(String organizationId) {
        this.organizationId = organizationId;
        this.environmentKeyMap = new ConcurrentHashMap<>();
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
        String normalizedEnv = environment.toLowerCase();
        String envKey = normalizedEnv.split("-", 2)[0];
        return environmentKeyMap.get(envKey);
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
}
