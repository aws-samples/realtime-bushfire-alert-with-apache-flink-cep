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

package com.amazonaws.prediction;

import com.amazonaws.events.kinesis.Event;
import com.amazonaws.events.kinesis.IoTEvent;

import java.util.List;
import java.util.Map;

/**
 * A simple Alert class
 */

public class Alert {
    Map<String, List<Event>> pattern;

    public Alert(Map<String, List<Event>> pattern) {
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        IoTEvent node1 = (IoTEvent) pattern.get("node1").get(0);
        IoTEvent node2 = (IoTEvent) pattern.get("node2").get(0);
        IoTEvent node3 = (IoTEvent) pattern.get("node3").get(0);
        IoTEvent node4 = (IoTEvent) pattern.get("node4").get(0);
        IoTEvent node5 = (IoTEvent) pattern.get("node5").get(0);

        return "[" + node1.getEventTime() + "] " +
                "Alert! Potential bushfire has been detected in this IoT sensor path: " +
                "N" + node1.getNode() + " --> " +
                "N" + node2.getNode() + " --> " +
                "N" + node3.getNode() + " --> " +
                "N" + node4.getNode() + " --> " +
                "N" + node5.getNode();
    }
}
