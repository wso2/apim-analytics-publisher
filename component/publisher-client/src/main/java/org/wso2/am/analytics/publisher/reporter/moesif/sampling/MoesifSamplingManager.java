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
package org.wso2.am.analytics.publisher.reporter.moesif.sampling;

import com.moesif.api.MoesifAPIClient;
import com.moesif.api.controllers.APIController;
import com.moesif.api.http.client.APICallBack;
import com.moesif.api.http.client.HttpContext;
import com.moesif.api.http.response.HttpResponse;
import com.moesif.api.models.AppConfigModel;
import com.moesif.api.models.EventModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.am.analytics.publisher.reporter.cloud.DefaultAnalyticsThreadFactory;
import org.wso2.am.analytics.publisher.util.HttpStatusHelper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Drives Moesif Dynamic Sampling for the analytics publisher.
 *
 * Periodically fetches Moesif's app config (per Moesif key) which carries the global, per-user, and per-company
 * sample rates, then uses the SDK's own helpers ({@code getSampleRateToUse}, {@code calculateWeight}) to decide,
 * for each event, whether to send it and what weight to stamp on it so Moesif can extrapolate metrics correctly.
 *
 * The manager keeps one cached {@link AppConfigModel} per Moesif API key so it works for both the single-key
 * publisher and the multi-tenant publisher (one config per org/key).
 */
public class MoesifSamplingManager {
    private static final Logger log = LogManager.getLogger(MoesifSamplingManager.class);

    private final boolean enabled;
    private final long refreshIntervalMs;
    private final int fallbackRate;
    private final Map<String, KeyState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public MoesifSamplingManager(boolean enabled, long refreshIntervalMs, int fallbackRate) {
        this.enabled = enabled;
        this.refreshIntervalMs = refreshIntervalMs;
        this.fallbackRate = clampRate(fallbackRate);
        if (enabled) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(
                    new DefaultAnalyticsThreadFactory("Moesif-Sampling-Refresh"));
            this.scheduler.scheduleAtFixedRate(this::safeRefreshAll,
                    refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
            log.info("Moesif dynamic sampling enabled (refresh={}ms, fallbackRate={})",
                    refreshIntervalMs, this.fallbackRate);
        } else {
            this.scheduler = null;
        }
    }

    private void safeRefreshAll() {
        try {
            refreshAll();
        } catch (Throwable t) {
            log.error("Failed to refresh Moesif sampling configuration", t);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Registers a Moesif API client under its key. The first registration triggers an initial config fetch;
     * subsequent calls are no-ops. Safe to call from multiple publish paths.
     */
    public void register(String moesifKey, MoesifAPIClient client) {
        if (!enabled || moesifKey == null || moesifKey.isEmpty() || client == null) {
            return;
        }
        states.computeIfAbsent(moesifKey, k -> {
            KeyState state = new KeyState(client, fallbackConfig());
            fetch(k, state);
            return state;
        });
    }

    /**
     * Returns true if the event should be published given the current sample rate.
     * When sampling is disabled or no config has been fetched yet, returns true (fail-open).
     */
    public boolean shouldSend(String moesifKey, EventModel event) {
        if (!enabled) {
            return true;
        }
        KeyState state = states.get(moesifKey);
        if (state == null || state.config == null) {
            return true;
        }
        int rate = clampRate(state.client.getAPI().getSampleRateToUse(event, state.config));
        if (rate >= 100) {
            return true;
        }
        if (rate <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < rate;
    }

    /**
     * Returns the weight to stamp on the event so Moesif can extrapolate metrics.
     * Defaults to 1 when sampling is disabled or no config is available.
     */
    public int weightFor(String moesifKey, EventModel event) {
        if (!enabled) {
            return 1;
        }
        KeyState state = states.get(moesifKey);
        if (state == null || state.config == null) {
            return 1;
        }
        APIController api = state.client.getAPI();
        int rate = clampRate(api.getSampleRateToUse(event, state.config));
        return api.calculateWeight(rate);
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void refreshAll() {
        for (Map.Entry<String, KeyState> entry : states.entrySet()) {
            try {
                fetch(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("Moesif app config refresh failed for one key", e);
            }
        }
    }

    private void fetch(String moesifKey, KeyState state) {
        APIController api = state.client.getAPI();
        try {
            api.getAppConfigAsync(new APICallBack<HttpResponse>() {
                @Override
                public void onSuccess(HttpContext context, HttpResponse response) {
                    int code = context.getResponse().getStatusCode();
                    if (!HttpStatusHelper.isSuccess(code)) {
                        log.warn("Moesif app config fetch returned status {} — keeping previous config", code);
                        return;
                    }
                    try {
                        AppConfigModel cfg = api.parseAppConfigModel(response.getRawBody());
                        if (cfg != null) {
                            state.config = cfg;
                            if (log.isDebugEnabled()) {
                                log.debug("Refreshed Moesif app config (sampleRate={}, userRates={}, companyRates={})",
                                        cfg.getSampleRate(),
                                        cfg.getUserSampleRate() == null ? 0 : cfg.getUserSampleRate().size(),
                                        cfg.getCompanySampleRate() == null ? 0 : cfg.getCompanySampleRate().size());
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to parse Moesif app config response", e);
                    }
                }

                @Override
                public void onFailure(HttpContext context, Throwable error) {
                    String msg = error == null ? "unknown" : error.getMessage();
                    log.warn("Moesif app config fetch failed: {} — keeping previous config", msg);
                }
            });
        } catch (Exception e) {
            log.error("Failed to schedule Moesif app config fetch", e);
        }
    }

    private AppConfigModel fallbackConfig() {
        AppConfigModel cfg = new AppConfigModel();
        cfg.setSampleRate(fallbackRate);
        return cfg;
    }

    private static int clampRate(int rate) {
        if (rate < 0) {
            return 0;
        }
        if (rate > 100) {
            return 100;
        }
        return rate;
    }

    private static final class KeyState {
        final MoesifAPIClient client;
        volatile AppConfigModel config;

        KeyState(MoesifAPIClient client, AppConfigModel initial) {
            this.client = client;
            this.config = initial;
        }
    }
}
