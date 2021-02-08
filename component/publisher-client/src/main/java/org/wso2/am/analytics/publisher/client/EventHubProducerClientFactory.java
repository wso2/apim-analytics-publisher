/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
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

package org.wso2.am.analytics.publisher.client;

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.auth.AuthClient;
import org.wso2.am.analytics.publisher.exception.AuthenticationException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Factory class to create EventHubProducerClient instance.
 */
public class EventHubProducerClientFactory {
    private static final Logger log = Logger.getLogger(EventHubClient.class);

    public static EventHubProducerClient create(String authEndpoint, String authToken) {
        TokenCredential tokenCredential = new WSO2TokenCredential(authEndpoint, authToken);
        String tempSASToken;
        try {
            // generate SAS token to get eventhub meta data
            tempSASToken = getSASToken(authEndpoint, authToken);
        } catch (AuthenticationException e) {
            throw new RuntimeException("SAS token generation failed.");
        }

        String resourceURI = getResourceURI(tempSASToken);
        String fullyQualifiedNamespace = getNamespace(resourceURI);
        String eventhubName = getEventHubName(resourceURI);
        return new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventhubName, tokenCredential)
                .buildProducerClient();
    }

    private static String getSASToken(String authEndpoint, String authToken) throws AuthenticationException {
        return AuthClient.getSASToken(authEndpoint, authToken);
    }

    /**
     * Extracts the resource URI from the SAS Token
     *
     * @param sasToken SAS token of the user
     * @return decoded resource URI from the token
     */
    private static String getResourceURI(String sasToken) {
        String[] sasAttributes = sasToken.split("&");
        String[] resource = sasAttributes[0].split("=");
        String resourceURI = "";
        try {
            resourceURI = URLDecoder.decode(resource[1], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            //never happens
        }
        //remove protocol append
        return resourceURI.replace("sb://", "");
    }

    private static String getNamespace(String resourceURI) {
        return resourceURI.split("/")[0];
    }

    private static String getEventHubName(String resourceURI) {
        return resourceURI.split("/", 2)[1];
    }
}
