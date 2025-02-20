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

import org.wso2.am.analytics.publisher.util.Constants;

import java.util.Map;

/**
 * Represents the AI token usage details, including prompt tokens,
 * total tokens, and completion tokens.
 * This helps in tracking API usage costs and resource consumption.
 */
public class AITokenUsage {
    private int promptTokens;
    private int totalTokens;
    private int completionTokens;

    /**
     * Default constructor.
     */
    public AITokenUsage() {
    }

    /**
     * Constructor to initialize from a Map
     */
    public AITokenUsage(Map<String, Object> map) {
        this.promptTokens = (int) map.get(Constants.AI_PROMPT_TOKEN_USAGE);
        this.totalTokens = (int) map.get(Constants.AI_TOTAL_TOKEN_USAGE);
        this.completionTokens = (int) map.get(Constants.AI_COMPLETION_TOKEN_USAGE);
    }


    /**
     * Gets the number of tokens used in the prompt.
     *
     * @return The number of prompt tokens.
     */
    public int getPromptTokens() {
        return promptTokens;
    }

    /**
     * Sets the number of tokens used in the prompt.
     *
     * @param promptTokens The number of prompt tokens.
     */
    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    /**
     * Gets the total number of tokens used in the request.
     *
     * @return The total number of tokens.
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * Sets the total number of tokens used in the request.
     *
     * @param totalTokens The total number of tokens.
     */
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * Gets the number of tokens used in the AI model's response.
     *
     * @return The number of completion tokens.
     */
    public int getCompletionTokens() {
        return completionTokens;
    }

    /**
     * Sets the number of tokens used in the AI model's response.
     *
     * @param completionTokens The number of completion tokens.
     */
    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
}
