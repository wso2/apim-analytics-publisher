package org.wso2.am.analytics.publisher.exception;

/**
 * Exception class for API calls related exceptions.
 * Could be used in scenarios where the issue is not strictly due to HTTP Client.
 */
public class APICallException extends Exception {
    public APICallException(String msg) {
        super(msg);
    }

    public APICallException(String msg, Throwable e) {
        super(msg, e);
    }
}
