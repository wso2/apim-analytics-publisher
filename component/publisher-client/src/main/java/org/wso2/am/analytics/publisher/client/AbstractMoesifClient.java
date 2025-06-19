package org.wso2.am.analytics.publisher.client;

import com.moesif.api.models.EventModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.util.Constants;

import java.io.IOException;
import java.util.Map;

/**
 * Abstract class representing a Moesif client for publishing events and building event responses.
 */
public abstract class AbstractMoesifClient {
    protected final Logger log = LogManager.getLogger(AbstractMoesifClient.class);

    /**
     * Publish method is responsible for publishing events to Moesif.
     */
    public abstract void publish(MetricEventBuilder builder) throws MetricReportingException;

    public abstract EventModel buildEventResponse(Map<String, Object> data)
            throws IOException, MetricReportingException;

    protected static void populateHeaders(Map<String, Object> data, Map<String, String> reqHeaders,
            Map<String, String> rspHeaders) {
        reqHeaders.put(Constants.MOESIF_USER_AGENT_KEY,
                (String) data.getOrDefault(Constants.USER_AGENT_HEADER, Constants.UNKNOWN_VALUE));
        reqHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, Constants.MOESIF_CONTENT_TYPE_HEADER);

        rspHeaders.put("Vary", "Accept-Encoding");
        rspHeaders.put("Pragma", "no-cache");
        rspHeaders.put("Expires", "-1");
        rspHeaders.put(Constants.MOESIF_CONTENT_TYPE_KEY, "application/json; charset=utf-8");
        rspHeaders.put("Cache-Control", "no-cache");
    }
}
