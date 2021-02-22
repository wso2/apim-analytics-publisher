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

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import org.apache.log4j.Logger;
import org.wso2.am.analytics.publisher.auth.AuthClient;
import org.wso2.am.analytics.publisher.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * WSO2 SAS token refresh implementation for TokenCredential
 */
class WSO2TokenCredential implements TokenCredential {
    private static final Logger log = Logger.getLogger(WSO2TokenCredential.class);
    private String authEndpoint;
    private String authToken;

    public WSO2TokenCredential(String authEndpoint, String authToken) {
        this.authEndpoint = authEndpoint;
        this.authToken = authToken;
    }

    @Override
    public Mono<AccessToken> getToken(TokenRequestContext tokenRequestContext) {
        log.debug("Trying to retrieving a new SAS token.");
        try {
            String sasToken = AuthClient.getSASToken(this.authEndpoint, this.authToken);
            log.debug("New SAS token retrieved.");
            // Using lower duration than actual.
            OffsetDateTime time = getExpirationTime(sasToken);
            return Mono.fromCallable(() -> new AccessToken(sasToken, time));
        } catch (AuthenticationException e) {
            log.error("Error occurred when retrieving SAS token.", e);
            throw new RuntimeException("Error occurred when retrieving SAS token.", e);
        }
    }

    private OffsetDateTime getExpirationTime(String sharedAccessSignature) {
        String[] parts = sharedAccessSignature.split("&");
        return Arrays.stream(parts).map(part -> part.split("="))
                .filter(pair -> pair.length == 2 && pair[0].equalsIgnoreCase("se"))
                .findFirst().map(pair -> pair[1])
                .map((expirationTimeStr) -> {
                    try {
                        long epochSeconds = Long.parseLong(expirationTimeStr);
                        return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC);
                    } catch (NumberFormatException e) {
                        log.error("Invalid expiration time format in the SAS token.", e);
                        return OffsetDateTime.MAX;
                    }
                })
                .orElse(OffsetDateTime.MAX);
    }
}
