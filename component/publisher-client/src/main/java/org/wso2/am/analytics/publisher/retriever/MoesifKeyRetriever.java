/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.am.analytics.publisher.retriever;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.exception.APICallException;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifKeyEntry;
import org.wso2.am.analytics.publisher.reporter.moesif.util.MoesifMicroserviceConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;


/**
 * Responsible for calling the Moesif microservice and refresh/init entire internal map(organization, moesif key) or
 * retrieving single moesif key.
 */
public class MoesifKeyRetriever {
    private static final Logger log = LoggerFactory.getLogger(MoesifKeyRetriever.class);
    private static MoesifKeyRetriever moesifKeyRetriever;
    private ConcurrentHashMap<String, String> orgIDMoesifKeyMap;
    private ConcurrentHashMap<String, String> orgIdEnvMap;
    // username of Moesif microservice
    private String msAuthUsername;
    // password of Moesif microservice
    private char[] msAuthPwd;
    private String moesifBasePath;

    private MoesifKeyRetriever(String authUsername, String authPwd, String moesifBasePath) {

        this.msAuthUsername = authUsername;
        this.msAuthPwd = authPwd.toCharArray();
        orgIDMoesifKeyMap = new ConcurrentHashMap();
        orgIdEnvMap = new ConcurrentHashMap();
        this.moesifBasePath = moesifBasePath;
    }

    public static synchronized MoesifKeyRetriever getInstance(String authUsername, String authPwd,
                                                              String moesifBasePath) {
        if (moesifKeyRetriever == null) {
            return new MoesifKeyRetriever(authUsername, authPwd, moesifBasePath);
        }
        return moesifKeyRetriever;
    }

    /**
     * Will initialize the empty orgID-MoesifKey map.
     * Will refresh/refill the  orgID-MoesifKey map.
     */
    public void initOrRefreshOrgIDMoesifKeyMap() {
        int attempts = MoesifMicroserviceConstants.NUM_RETRY_ATTEMPTS;
        try {
            callListResource();
        } catch (IOException | APICallException ex) {
            // TODO: Separate retry logic to a separate class.
            log.error("First attempt of refreshing internal map failed,retrying.", ex);

            while (attempts > 0) {
                attempts--;
                try {
                    Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    callListResource();
                } catch (IOException | APICallException e) {
                    log.error("Retry attempt failed.", e);
                }
            }
        }
    }

    /**
     * Will retrieve single Moesif key corresponding to given organization ID.
     *
     * @param orgID
     * @return Moesif Key corresponding orgID
     */
    public String getMoesifKey(String orgID) {
        String response;
        int attempts = MoesifMicroserviceConstants.NUM_RETRY_ATTEMPTS;
        try {
            response = callDetailResource(orgID);
        } catch (IOException | APICallException ex) {
            // TODO: Separate retry logic to a separate class.
            log.error("First attempt of single moesif key fetch failed, retrying.", ex);

            while (attempts > 0) {
                attempts--;
                try {
                    Thread.sleep(MoesifMicroserviceConstants.TIME_TO_WAIT);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    response = callDetailResource(orgID);
                    return response;
                } catch (IOException | APICallException e) {
                    log.error("Retry attempt failed.", e);
                }
            }
            response = null;
        }
        return response;
    }

    /**
     * Will remove Moesif key corresponding to organization ID.
     * Existing Moesif SDK client associated with the moesif key will be also removed.
     *
     * @param orgID
     */
    public void removeMoesifKeyFromMap(String orgID) {
        orgIDMoesifKeyMap.remove(orgID);
    }

    /**
     * Fetches all the available orgID - MoesifKey pairs from the microservice.
     *
     * @throws IOException
     * @throws APICallException
     */
    private void callListResource() throws IOException, APICallException {
        final URL obj;
        String url = this.moesifBasePath + MoesifMicroserviceConstants.MOESIF_EP_COMMON_PATH;
        try {
            obj = new URL(url);
        } catch (MalformedURLException ex) {
            log.error("Failed calling Moesif microservice. Attempted to call url: {}",
                    url.replaceAll("[\r\n]", ""),
                    ex.getMessage().replaceAll("[\r\n]", ""));
            return;
        }
        String authHeaderValue = getAuthHeader(msAuthUsername, msAuthPwd);
        HttpURLConnection con = null;
        try {
            if (obj.getProtocol().equals("https")) {
                con = (HttpsURLConnection) obj.openConnection();
            } else {
                con = (HttpURLConnection) obj.openConnection();
            }
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", authHeaderValue);
            con.setRequestProperty("Content-Type", MoesifMicroserviceConstants.CONTENT_TYPE);
            con.setReadTimeout(MoesifMicroserviceConstants.REQUEST_READ_TIMEOUT);
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));) {
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    updateMap(response.toString());
                }
            } else if (responseCode >= 400 && responseCode < 500) {
                log.error("Getting {} from the microservice.", responseCode);
            } else {
                throw new APICallException("Getting " + responseCode + " from the Moesif microservice and retrying.");
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    /**
     * Retrieves moesif key of the given orgID by calling the microservice.
     *
     * @param orgID
     * @return moesif key of the orgID
     * @throws IOException
     * @throws APICallException
     */
    private String callDetailResource(String orgID) throws IOException, APICallException {
        StringBuffer response = new StringBuffer();
        String url = this.moesifBasePath + MoesifMicroserviceConstants.MOESIF_EP_COMMON_PATH;
        // Protecting endpoint from SSRF.
        if (!orgID.isEmpty()) {
            url = url + "/" + "?" + MoesifMicroserviceConstants.QUERY_PARAM + "=" +
                    orgID;
        } else {
            log.error("Failed calling Moesif microservice. Organization ID cannot be empty");
            return null;
        }
        final URL obj;
        try {
            obj = new URL(url);
        } catch (MalformedURLException ex) {
            log.error("Failed calling Moesif microservice. Attempted to call url: {}", url.replaceAll("[\r\n]", ""),
                    ex.getMessage().replaceAll("[\r\n]", ""));
            return null;
        }
        String authHeaderValue = getAuthHeader(msAuthUsername, msAuthPwd);
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", MoesifMicroserviceConstants.CONTENT_TYPE);
            con.setRequestProperty("Authorization", authHeaderValue);
            con.setReadTimeout(MoesifMicroserviceConstants.REQUEST_READ_TIMEOUT);
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));) {

                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    updateMoesifKey(response.toString());
                    return response.toString();
                }
            } else if (responseCode >= 400 && responseCode < 500) {
                log.error("Getting {} from the Moesif microservice.", responseCode);
                return null;
            } else {
                throw new APICallException("Getting " + responseCode + " from the Moesif microservice and retrying.");
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private String getAuthHeader(String msAuthUsername, char[] msAuthPwd) {
        String auth = msAuthUsername + ":" + String.valueOf(msAuthPwd);
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        return authHeaderValue;
    }

    private synchronized void updateMoesifKey(String response) {
        Gson gson = new Gson();
        String json = response;
        MoesifKeyEntry newKey = gson.fromJson(json, MoesifKeyEntry.class);
        String orgID = newKey.getOrganization_id();
        String moesifKey = newKey.getMoesif_key();
        String env = newKey.getEnv();
        orgIDMoesifKeyMap.put(orgID, moesifKey);
        orgIdEnvMap.put(orgID, env);
    }

    private synchronized void updateMap(String response) {
        Gson gson = new Gson();
        String json = response;

        Type collectionType = new CustomType().getType();
        Collection<MoesifKeyEntry> newKeys = gson.fromJson(json, collectionType);

        for (MoesifKeyEntry entry : newKeys) {
            String orgID = entry.getOrganization_id();
            String env = entry.getEnv();
            String moesifKey = entry.getMoesif_key();
            orgIDMoesifKeyMap.put(orgID, moesifKey);
            orgIdEnvMap.put(orgID, env);
        }
    }

    /**
     * returning orgID-MoesifKey map.
     *
     * @return orgIDMoesifKeyMap
     */
    public ConcurrentHashMap<String, String> getMoesifKeyMap() {
        return orgIDMoesifKeyMap;
    }

    public ConcurrentHashMap<String, String> getEnvMap() {
        return orgIdEnvMap;
    }

    public void clearMoesifKeyMap() {
        if (!orgIDMoesifKeyMap.isEmpty()) {
            orgIDMoesifKeyMap.clear();
        }
    }

    public void clearEnvMap() {
        if (!orgIdEnvMap.isEmpty()) {
            orgIdEnvMap.clear();
        }
    }

    static class CustomType extends TypeToken<Collection<MoesifKeyEntry>> {
        // nothing to add
    }

}
