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

import java.util.Map;

/**
 * Represents a Moesif event data wrapper.
 * Encapsulates the raw event data map for type safety.
 */
public class MoesifEventData {
    private final Map<String, Object> eventData;

    /**
     * Constructor with event data map.
     *
     * @param eventData The event data map.
     */
    public MoesifEventData(Map<String, Object> eventData) {
        this.eventData = eventData;
    }

    /**
     * Gets the raw event data map.
     *
     * @return The event data map.
     */
    public Map<String, Object> getEventData() {
        return eventData;
    }

    /**
     * Gets a value from the event data by key.
     *
     * @param key The key to retrieve.
     * @return The value, or null if not found.
     */
    public Object get(String key) {
        return eventData.get(key);
    }

    /**
     * Checks if the event data contains a specific key.
     *
     * @param key The key to check.
     * @return true if the key exists, false otherwise.
     */
    public boolean containsKey(String key) {
        return eventData.containsKey(key);
    }
}
