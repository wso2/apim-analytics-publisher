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

package org.wso2.am.analytics.publisher;

import org.apache.log4j.Logger;
import org.testng.annotations.Test;
import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricReporter;
import org.wso2.am.analytics.publisher.reporter.MetricReporterFactory;

import java.util.HashMap;
import java.util.Map;

public class LogMetricReporterTestCase {
    private static final Logger log = Logger.getLogger(MetricReporterTestCase.class);


    @Test
    public void testMetricReporterCreationWithoutConfigs() throws MetricCreationException, MetricReportingException {
        MetricReporter metricReporter = MetricReporterFactory.getInstance().createMetricReporter(
                "org.wso2.am.analytics.publisher.reporter.log.LogMetricReporter", null);
        CounterMetric metric = metricReporter.createCounterMetric("testCounter", null);

        Map<String, String> event = new HashMap<>();
        event.put("attribute1", "value1");
        event.put("attribute2", "value2");
        event.put("attribute3", "value3");
        event.put("attribute4", "value4");
        metric.incrementCount(event);
    }
}
