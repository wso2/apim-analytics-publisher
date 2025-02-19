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

/**
 * Represents the properties associated with an API request,
 * including routing information, AI-related metadata, and AI token usage.
 */
public class Properties {
    private boolean isEgress;
    private String subType;
    private AIMetadata aiMetadata;
    private AITokenUsage aiTokenUsage;

    /**
     * Default constructor.
     */
    public Properties() {
    }

    /**
     * Checks if the request is an egress request.
     *
     * @return {@code true} if the request is egress, {@code false} otherwise.
     */
    public boolean isEgress() {
        return isEgress;
    }

    /**
     * Sets whether the request is an egress request.
     *
     * @param egress {@code true} if the request is egress, {@code false} otherwise.
     */
    public void setEgress(boolean egress) {
        isEgress = egress;
    }

    /**
     * Gets the subtype of the API request.
     *
     * @return The API request subtype.
     */
    public String getSubType() {
        return subType;
    }

    /**
     * Sets the subtype of the API request.
     *
     * @param subType The API request subtype.
     */
    public void setSubType(String subType) {
        this.subType = subType;
    }

    /**
     * Gets the AI metadata associated with this request.
     *
     * @return The {@link AIMetadata} instance.
     */
    public AIMetadata getAiMetadata() {
        return aiMetadata;
    }

    /**
     * Sets the AI metadata for this request.
     *
     * @param aiMetadata The {@link AIMetadata} instance.
     */
    public void setAiMetadata(AIMetadata aiMetadata) {
        this.aiMetadata = aiMetadata;
    }

    /**
     * Gets the AI token usage details for this request.
     *
     * @return The {@link AITokenUsage} instance.
     */
    public AITokenUsage getAiTokenUsage() {
        return aiTokenUsage;
    }

    /**
     * Sets the AI token usage details for this request.
     *
     * @param aiTokenUsage The {@link AITokenUsage} instance.
     */
    public void setAiTokenUsage(AITokenUsage aiTokenUsage) {
        this.aiTokenUsage = aiTokenUsage;
    }

    @Override
    public String toString() {
        return "Properties{" +
                "isEgress=" + isEgress +
                ", subType='" + subType + '\'' +
                ", aiMetadata=" + aiMetadata +
                ", aiTokenUsage=" + aiTokenUsage +
                '}';
    }
}
