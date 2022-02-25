/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.am.analytics.publisher.auth;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.util.Constants;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Util class to generate http client with proxy configurations
 */
public class AuthProxyUtils {

    private static final Logger log = LoggerFactory.getLogger(AuthProxyUtils.class);
    private static KeyStore trustStore;
    private static TrustManagerFactory trustManagerFactory;
    private static final String KEYSTORE_TYPE = "JKS";
    private static final String PROTOCOL = "TLS";
    private static final String ALGORITHM = "SunX509";

    public static okhttp3.OkHttpClient getProxyClient(Map<String, String> properties) {

        String proxyHost = properties.get(Constants.PROXY_HOST);
        String proxyPort = properties.get(Constants.PROXY_PORT);
        String proxyUsername = properties.get(Constants.PROXY_USERNAME);
        String proxyPassword = properties.get(Constants.PROXY_PASSWORD);

        OkHttpClient.Builder okHttpClientBuilder;
        SSLSocketFactory sslSocketFactory = getTrustedSSLSocketFactory(properties);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
        }
        okHttpClientBuilder = new okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0])
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))));

        if (StringUtils.isNotEmpty(proxyUsername) && StringUtils.isNotEmpty(proxyPassword)) {
            Authenticator proxyAuthenticator = (route, response) -> {
                String credential = Credentials.basic(proxyUsername, proxyPassword);
                return response.request().newBuilder().header("Proxy-Authorization", credential).build();
            };
            okHttpClientBuilder.proxyAuthenticator(proxyAuthenticator);
        }

        return okHttpClientBuilder.build();
    }

    private static SSLSocketFactory getTrustedSSLSocketFactory(Map<String, String> configProperties) {

        try {
            String keyStorePassword = configProperties.get(Constants.KEYSTORE_PASSWORD);
            String keyStoreLocation = configProperties.get(Constants.KEYSTORE_LOCATION);
            String trustStorePassword = configProperties.get(Constants.TRUSTSTORE_PASSWORD);
            String trustStoreLocation = configProperties.get(Constants.TRUSTSTORE_LOCATION);

            KeyStore keyStore = loadKeyStore(keyStoreLocation, keyStorePassword);
            trustStore = loadTrustStore(trustStoreLocation, trustStorePassword);
            return initSSLConnection(keyStore, keyStorePassword);
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | CertificateException
                | IOException | UnrecoverableKeyException e) {
            log.error("Error while creating the SSL socket factory", e);
            return null;
        }
    }

    private static SSLSocketFactory initSSLConnection(KeyStore keyStore, String keyStorePassword) throws
            NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ALGORITHM);
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        trustManagerFactory = TrustManagerFactory.getInstance(ALGORITHM);
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        SSLContext.setDefault(sslContext);
        return sslContext.getSocketFactory();
    }

    private static KeyStore loadKeyStore(String keyStorePath, String ksPassword) throws KeyStoreException,
            IOException, CertificateException, NoSuchAlgorithmException {

        InputStream fileInputStream = null;
        try {
            char[] keypassChar = ksPassword.toCharArray();
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            fileInputStream = new FileInputStream(keyStorePath);
            keyStore.load(fileInputStream, keypassChar);
            return keyStore;
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }
    }

    private static KeyStore loadTrustStore(String trustStorePath, String tsPassword) throws KeyStoreException,
            IOException, CertificateException, NoSuchAlgorithmException {

        return loadKeyStore(trustStorePath, tsPassword);
    }
}
