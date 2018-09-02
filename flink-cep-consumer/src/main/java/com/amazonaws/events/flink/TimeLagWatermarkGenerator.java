/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may
 * not use this file except in compliance with the License. A copy of the
 * License is located at
 *
 *    http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.events.flink;

import com.amazonaws.events.kinesis.Event;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;

/**
 * A simple watermark generator class which generates watermarks that are lagging behind processing time by a fixed amount.
 * It assumes that elements arrive in Apache Flink after a bounded delay.
 */

public class TimeLagWatermarkGenerator
        implements AssignerWithPeriodicWatermarks<Event> {

    private final long maxTimeLagInMillisecond = 30*1000; // 30 s - Maximum acceptable time lag

    /**
     * Extracts the event timestamp from the consumed stream events
     * @param event
     * @param previousElementTimestamp
     * @return
     * https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/functions/TimestampAssigner.html#extractTimestamp-T-long-
     */
    @Override
    public long extractTimestamp(Event event, long previousElementTimestamp) {
        return event.getEventTimestamp();
    }

    /**
     * Generates the current Watermark
     * @return
     * https://ci.apache.org/projects/flink/flink-docs-release-1.4/api/java/org/apache/flink/streaming/api/functions/AssignerWithPeriodicWatermarks.html#getCurrentWatermark--
     */
    @Override
    public Watermark getCurrentWatermark() {
        // Return the watermark as current time minus the maximum time lag
        return new Watermark(System.currentTimeMillis() - maxTimeLagInMillisecond);
    }
}