/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.reporter.moesif.util;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.options.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.util.Constants;

import java.util.Map;

import static org.wso2.am.analytics.publisher.util.Constants.HTTP_PROTOCOL;

/**
 * Util class to route Moesif SDK traffic through an HTTP proxy. The Moesif SDK sends events using the embedded
 * Unirest client, whose HTTP clients are held statically. Hence the proxy is applied once, globally for this bundle,
 * before any Moesif client is created.
 */
public class MoesifProxyUtils {

    private static final Logger log = LogManager.getLogger(MoesifProxyUtils.class);

    // Same defaults Unirest uses when it builds its own clients.
    private static final int CONNECTION_TIMEOUT = (int) Options.CONNECTION_TIMEOUT;
    private static final int SOCKET_TIMEOUT = 60000;
    private static final int MAX_PORT = 65535;

    private MoesifProxyUtils() {

    }

    /**
     * Configures the Unirest clients used by the Moesif SDK to connect through the proxy defined by the
     * {@code proxy_config_*} analytics properties. Does nothing if {@code proxy_config_enable} is not true.
     *
     * @param properties analytics configuration properties
     * @throws MetricCreationException if proxy is enabled but the proxy configuration is invalid
     */
    public static void configureProxyIfEnabled(Map<String, String> properties) throws MetricCreationException {

        if (!Boolean.parseBoolean(properties.get(Constants.PROXY_ENABLE))) {
            return;
        }
        String proxyHost = properties.get(Constants.PROXY_HOST);
        String proxyPortValue = properties.get(Constants.PROXY_PORT);
        String proxyProtocol = properties.get(Constants.PROXY_PROTOCOL);
        String proxyUsername = properties.get(Constants.PROXY_USERNAME);
        String proxyPassword = properties.get(Constants.PROXY_PASSWORD);

        if (StringUtils.isBlank(proxyHost)) {
            throw new MetricCreationException(Constants.PROXY_HOST + " is required when " + Constants.PROXY_ENABLE
                    + " is set to true.");
        }
        int proxyPort;
        try {
            proxyPort = Integer.parseInt(StringUtils.trim(proxyPortValue));
        } catch (NumberFormatException e) {
            throw new MetricCreationException("Invalid " + Constants.PROXY_PORT + " value: " + proxyPortValue, e);
        }
        if (proxyPort < 1 || proxyPort > MAX_PORT) {
            throw new MetricCreationException("Invalid " + Constants.PROXY_PORT + " value: " + proxyPortValue
                    + ". The port must be between 1 and " + MAX_PORT + ".");
        }
        if (StringUtils.isBlank(proxyProtocol)) {
            proxyProtocol = HTTP_PROTOCOL;
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPort, proxyProtocol);
        if (StringUtils.isBlank(proxyUsername) || StringUtils.isBlank(proxyPassword)) {
            // Unirest rebuilds its default sync and async clients with the proxy set.
            Unirest.setProxy(proxy);
        } else {
            // Unirest.setProxy() does not support proxy authentication, hence provide our own clients.
            RequestConfig requestConfig = RequestConfig.custom()
                    .setProxy(proxy)
                    .setConnectTimeout(CONNECTION_TIMEOUT)
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .setConnectionRequestTimeout(SOCKET_TIMEOUT)
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .build();
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(proxyHost, proxyPort),
                    new UsernamePasswordCredentials(proxyUsername, proxyPassword));

            Unirest.setAsyncHttpClient(HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setMaxConnTotal(Options.MAX_TOTAL)
                    .setMaxConnPerRoute(Options.MAX_PER_ROUTE)
                    .build());
            Unirest.setHttpClient(HttpClientBuilder.create()
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setMaxConnTotal(Options.MAX_TOTAL)
                    .setMaxConnPerRoute(Options.MAX_PER_ROUTE)
                    .build());
        }
        log.info("Moesif analytics publishing is configured to use the proxy {}://{}:{}",
                proxyProtocol, proxyHost, proxyPort);
    }
}
