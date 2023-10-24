package org.wso2.am.analytics.publisher.retriever;

import org.wso2.am.analytics.publisher.util.Constants;

import java.util.Map;

/**
 * This Class used to configure moseif config key base retriever.
 */
public class ConfigBaseMoesifKeyRetriever implements MoesifKeyRetriever {

    private final String key;

    public ConfigBaseMoesifKeyRetriever(Map<String, String> properties) {

        key = properties.get(Constants.MOESIF_KEY_VALUE);

    }

    @Override
    public String getKey(String organization, String environment) {

        return key;
    }
}
