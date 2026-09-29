/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
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

package org.wso2.am.analytics.publisher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.util.Constants;
import org.wso2.am.analytics.publisher.util.UserAgentParser;
import ua_parser.Client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class UserAgentParserTestCase {
    private static final Logger log = LogManager.getLogger(UserAgentParserTestCase.class);
    private static final String USER_AGENT = "SampleApp/1.0.0 (Linux; Android 14; SDK 34; Generic Device; en) "
            + "DeviceId/%s AppBuild/100";

    @Test
    public void testParseUserAgent() {
        Client client = UserAgentParser.getInstance().parseUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        Assert.assertEquals(client.userAgent.family, "Chrome");
        Assert.assertEquals(client.os.family, "Windows");

        String userAgent = String.format(USER_AGENT, UUID.randomUUID());
        Client first = UserAgentParser.getInstance().parseUserAgent(userAgent);
        Assert.assertEquals(first.os.family, "Android");
        Assert.assertSame(UserAgentParser.getInstance().parseUserAgent(userAgent), first,
                "Repeated user agent should be served from the cache");
    }

    @Test
    public void testCacheIsBoundedUnderConcurrentAccess() throws Exception {
        log.info("Running user agent cache concurrency test case");
        int threads = 16;
        int perThread = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        UserAgentParser.getInstance().parseUserAgent(String.format(USER_AGENT, UUID.randomUUID()));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
        Map<?, ?> cache = getClientCache();
        Assert.assertTrue(cache.size() <= Constants.USER_AGENT_DEFAULT_CACHE_SIZE,
                "User agent cache grew beyond its limit under concurrent access. Size: " + cache.size());

        // Eviction must keep working after concurrent access.
        for (int i = 0; i < 500; i++) {
            UserAgentParser.getInstance().parseUserAgent(String.format(USER_AGENT, UUID.randomUUID()));
        }
        Assert.assertEquals(cache.size(), Constants.USER_AGENT_DEFAULT_CACHE_SIZE);
    }

    private Map<?, ?> getClientCache() throws Exception {
        Field field = UserAgentParser.class.getDeclaredField("clientCache");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(UserAgentParser.getInstance());
    }
}
