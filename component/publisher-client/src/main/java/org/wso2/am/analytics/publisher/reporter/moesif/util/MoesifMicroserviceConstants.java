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
package org.wso2.am.analytics.publisher.reporter.moesif.util;

/**
 * Class for constants related to external Moesif microservice.
 */
public class MoesifMicroserviceConstants {
    public static final String MOESIF_PROTOCOL_WITH_FQDN_KEY = "moesifProtocolWithFQDN";
    public static final String MOESIF_EP_COMMON_PATH = "moesif/moesif_key";
    public static final String MOESIF_MS_VERSIONING_KEY = "moesifMSVersioning";
    public static final String MS_USERNAME_CONFIG_KEY = "moesifMSAuthUsername";
    public static final String MS_PWD_CONFIG_KEY = "moesifMSAuthPwd";
    public static final String CONTENT_TYPE = "application/json";
    public static final String QUERY_PARAM = "org_id";
    public static final int NUM_RETRY_ATTEMPTS = 3;
    public static final long TIME_TO_WAIT = 10000;
    public static final int NUM_RETRY_ATTEMPTS_PUBLISH = 3;
    public static final long TIME_TO_WAIT_PUBLISH = 10000;
    public static final int REQUEST_READ_TIMEOUT = 10000;
    public static final long PERIODIC_CALL_DELAY = 300000;

    // Dynamic sampling config (driven by Moesif app config endpoint)
    public static final String SAMPLING_ENABLED_KEY = "sampling_enabled";
    public static final String SAMPLING_REFRESH_INTERVAL_KEY = "sampling_refresh_interval_ms";
    public static final String SAMPLING_FALLBACK_RATE_KEY = "sampling_fallback_rate";
    public static final long DEFAULT_SAMPLING_REFRESH_INTERVAL_MS = 60000;
    public static final int DEFAULT_SAMPLING_FALLBACK_RATE = 100;

    // In-memory retry buffer (per Moesif key) for when Moesif is unreachable.
    public static final String RETRY_BUFFER_ENABLED_KEY = "retry_buffer_enabled";
    public static final String RETRY_BUFFER_SIZE_KEY = "retry_buffer_size";
    public static final String RETRY_INTERVAL_SECONDS_KEY = "retry_interval_seconds";
    public static final String RETRY_LOG_MULTIPLIER_KEY = "retry_log_multiplier";
    public static final String RETRY_DRAIN_BURST_SIZE_KEY = "retry_drain_burst_size";
    public static final String RETRY_DRAIN_BATCH_DELAY_MS_KEY = "retry_drain_batch_delay_ms";
    public static final boolean DEFAULT_RETRY_BUFFER_ENABLED = true;
    public static final int DEFAULT_RETRY_BUFFER_SIZE = 10000;
    public static final long DEFAULT_RETRY_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_RETRY_LOG_MULTIPLIER = 10;
    public static final int DEFAULT_RETRY_DRAIN_BURST_SIZE = 5;
    public static final long DEFAULT_RETRY_DRAIN_BATCH_DELAY_MS = 100;
}
