/**
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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
package org.wso2.am.analytics.publisher.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
/**
 * Utility class for handling HTTP status codes.
 * Provides methods to determine the type of HTTP response (success, client error, server error)
 * and whether a request should be retried based on the status code.
 */
public class HttpStatusHelper {
    // HTTP Status Code Ranges
    public static final int SUCCESS_RANGE_START = 200;
    public static final int SUCCESS_RANGE_END = 299;
    public static final int CLIENT_ERROR_RANGE_START = 400;
    public static final int CLIENT_ERROR_RANGE_END = 499;
    public static final int SERVER_ERROR_RANGE_START = 500;
    public static final int SERVER_ERROR_RANGE_END = 599;

    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(408, 429)));
    /**
     * Checks if the HTTP status code indicates a successful response.
     * Success range: 200-299
     *
     * @param statusCode HTTP status code to check
     * @return true if status code is in success range (2xx)
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= SUCCESS_RANGE_START &&
                statusCode <= SUCCESS_RANGE_END;
    }

    /**
     * Checks if the HTTP status code indicates a client error.
     * Client error range: 400-499
     *
     * @param statusCode HTTP status code to check
     * @return true if status code is in client error range (4xx)
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= CLIENT_ERROR_RANGE_START &&
                statusCode < CLIENT_ERROR_RANGE_END;
    }

    /**
     * Checks if the HTTP status code indicates a server error.
     * Server error range: 500-599
     *
     * @param statusCode HTTP status code to check
     * @return true if status code is in server error range (5xx)
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= SERVER_ERROR_RANGE_START &&
                statusCode < SERVER_ERROR_RANGE_END;
    }

    /**
     * Determine if the request should be retried based on status code.
     * Retryable conditions: server errors (5xx) and specific client errors (408, 429).
     * @param statusCode HTTP status code
     * @return true if request should be retried
     */
    public static boolean shouldRetry(int statusCode) {
        return isServerError(statusCode) || RETRYABLE_CLIENT_ERRORS.contains(statusCode);
    }
}
