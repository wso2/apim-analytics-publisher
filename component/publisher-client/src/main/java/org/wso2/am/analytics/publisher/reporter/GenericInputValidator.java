package org.wso2.am.analytics.publisher.reporter;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.wso2.am.analytics.publisher.util.Constants.API_CREATION;
import static org.wso2.am.analytics.publisher.util.Constants.API_CREATOR_TENANT_DOMAIN;
import static org.wso2.am.analytics.publisher.util.Constants.API_ID;
import static org.wso2.am.analytics.publisher.util.Constants.API_METHOD;
import static org.wso2.am.analytics.publisher.util.Constants.API_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.API_RESOURCE_TEMPLATE;
import static org.wso2.am.analytics.publisher.util.Constants.API_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.API_VERSION;
import static org.wso2.am.analytics.publisher.util.Constants.API_CONTEXT;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.APPLICATION_OWNER;
import static org.wso2.am.analytics.publisher.util.Constants.BACKEND_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.CORRELATION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.DESTINATION;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_MESSAGE;
import static org.wso2.am.analytics.publisher.util.Constants.ERROR_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.GATEWAY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.KEY_TYPE;
import static org.wso2.am.analytics.publisher.util.Constants.PROXY_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.REGION_ID;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.REQUEST_TIMESTAMP;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_CACHE_HIT;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.RESPONSE_MEDIATION_LATENCY;
import static org.wso2.am.analytics.publisher.util.Constants.TARGET_RESPONSE_CODE;
import static org.wso2.am.analytics.publisher.util.Constants.USER_AGENT_HEADER;
import static org.wso2.am.analytics.publisher.util.Constants.USER_NAME;
import static org.wso2.am.analytics.publisher.util.Constants.USER_IP;

/**
 * Generic Input Validator for any event metric reporter. In default required attributes include userName. Validator holds all required attributes against which
 * inputs will be validated.
 */
public class GenericInputValidator {
    private static final org.wso2.am.analytics.publisher.reporter.GenericInputValidator INSTANCE = new org.wso2.am.analytics.publisher.reporter.GenericInputValidator();
    private static final Map<String, Class> defaultResponseEventSchema = Stream.of(
                    new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_METHOD, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_CONTEXT, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_RESOURCE_TEMPLATE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(DESTINATION, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(USER_AGENT_HEADER, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(USER_NAME, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
                    new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class),
                    new AbstractMap.SimpleImmutableEntry<>(RESPONSE_CACHE_HIT, Boolean.class),
                    new AbstractMap.SimpleImmutableEntry<>(RESPONSE_LATENCY, Long.class),
                    new AbstractMap.SimpleImmutableEntry<>(BACKEND_LATENCY, Long.class),
                    new AbstractMap.SimpleImmutableEntry<>(REQUEST_MEDIATION_LATENCY, Long.class),
                    new AbstractMap.SimpleImmutableEntry<>(RESPONSE_MEDIATION_LATENCY, Long.class),
                    new AbstractMap.SimpleImmutableEntry<>(USER_IP, String.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Class> defaultFaultEventSchema = Stream.of(
                    new AbstractMap.SimpleImmutableEntry<>(REQUEST_TIMESTAMP, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(CORRELATION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(KEY_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(ERROR_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(ERROR_CODE, Integer.class),
                    new AbstractMap.SimpleImmutableEntry<>(ERROR_MESSAGE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_NAME, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_VERSION, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_CREATION, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(API_CREATOR_TENANT_DOMAIN, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_NAME, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(APPLICATION_OWNER, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(REGION_ID, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(GATEWAY_TYPE, String.class),
                    new AbstractMap.SimpleImmutableEntry<>(PROXY_RESPONSE_CODE, Integer.class),
                    new AbstractMap.SimpleImmutableEntry<>(TARGET_RESPONSE_CODE, Integer.class))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final List<String> configProperties = new ArrayList<>();

    private GenericInputValidator() {
        //private constructor
    }

    public static org.wso2.am.analytics.publisher.reporter.GenericInputValidator getInstance() {
        return INSTANCE;
    }

    public Map<String, Class> getEventProperties(MetricSchema schema) {
        switch (schema) {
            case RESPONSE:
                return defaultResponseEventSchema;
            case ERROR:
                return defaultFaultEventSchema;
            default:
                return new HashMap<>();
        }
    }

    public List<String> getConfigProperties() {
        return configProperties;
    }
}
