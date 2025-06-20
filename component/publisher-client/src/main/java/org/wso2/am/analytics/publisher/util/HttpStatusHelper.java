package org.wso2.am.analytics.publisher.util;

import java.util.Set;

public class HttpStatusHelper {
    // HTTP Status Code Ranges
    public static final int SUCCESS_RANGE_START = 200;
    public static final int SUCCESS_RANGE_END = 299;
    public static final int CLIENT_ERROR_RANGE_START = 400;
    public static final int CLIENT_ERROR_RANGE_END = 499;
    public static final int SERVER_ERROR_RANGE_START = 500;
    public static final int SERVER_ERROR_RANGE_END = 599;

    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(408, 429);
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
