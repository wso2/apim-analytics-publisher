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

package org.wso2.am.analytics.publisher.reporter.cloud;

import org.wso2.am.analytics.publisher.exception.MetricCreationException;
import org.wso2.am.analytics.publisher.exception.MetricReportingException;
import org.wso2.am.analytics.publisher.reporter.CounterMetric;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.reporter.MetricSchema;

/**
 * Implementation of {@link CounterMetric} for Choroe Metric Reporter
 */
public class DefaultCounterMetric implements CounterMetric {
    private String name;
    private EventQueue queue;
    private MetricSchema schema;

    public DefaultCounterMetric(String name, EventQueue queue, MetricSchema schema) throws MetricCreationException {
        //Constructor should be made protected. Keeping public till testing plan is finalized
        this.name = name;
        this.queue = queue;
        if (schema == MetricSchema.ERROR || schema == MetricSchema.RESPONSE) {
            this.schema = schema;
        } else {
            throw new MetricCreationException("Default Counter Metric only supports " + MetricSchema.RESPONSE + " and"
                                                      + " " + MetricSchema.ERROR + " types.");
        }

    }

    @Override
    public String getName() {
        return name;
    }

    @Override public MetricSchema getSchema() {
        return schema;
    }

    @Override
    public int incrementCount(MetricEventBuilder builder) throws MetricReportingException {
        if (builder != null) {
            queue.put(builder);
            return 0;
        } else {
            throw new MetricReportingException("MetricEventBuilder cannot be null");
        }
    }

    /**
     * Returns Event Builder used for this CounterMetric. Depending on the schema different types of builders will be
     * returned.
     *
     * @return return {@link MetricEventBuilder} for this {@link CounterMetric}
     */
    @Override
    public MetricEventBuilder getEventBuilder() {
        if (schema == MetricSchema.RESPONSE) {
            return new DefaultResponseMetricEventBuilder();
        } else if (schema == MetricSchema.ERROR) {
            return new DefaultFaultMetricEventBuilder();
        }
        //will not happen
        return null;
    }
}
