/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.am.analytics.publisher.properties;

import java.util.Map;

/**
 * Represents metadata information about the AI model used in API processing.
 * This includes the vendor details, model name, and version.
 */
public class AIMetadata {
    private String vendorName;
    private String model;
    private String vendorVersion;

    /**
     * Default constructor.
     */
    public AIMetadata() {
    }

    /**
     * Constructor to initialize from a Map
     */
    public AIMetadata(Map<String, Object> map) {
        this.vendorName = (String) map.get("vendorName");
        this.model = (String) map.get("model");
        this.vendorVersion = (String) map.get("vendorVersion");
    }

    /**
     * Gets the name of the AI model vendor.
     *
     * @return The AI vendor name.
     */
    public String getVendorName() {
        return vendorName;
    }

    /**
     * Sets the name of the AI model vendor.
     *
     * @param vendorName The AI vendor name.
     */
    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    /**
     * Gets the AI model name.
     *
     * @return The AI model name.
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the AI model name.
     *
     * @param model The AI model name.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Gets the version of the AI model provided by the vendor.
     *
     * @return The AI model vendor version.
     */
    public String getVendorVersion() {
        return vendorVersion;
    }

    /**
     * Sets the version of the AI model provided by the vendor.
     *
     * @param vendorVersion The AI model vendor version.
     */
    public void setVendorVersion(String vendorVersion) {
        this.vendorVersion = vendorVersion;
    }

    @Override
    public String toString() {
        return "AIMetadata{" +
                "vendorName='" + vendorName + '\'' +
                ", model='" + model + '\'' +
                ", vendorVersion='" + vendorVersion + '\'' +
                '}';
    }
}
