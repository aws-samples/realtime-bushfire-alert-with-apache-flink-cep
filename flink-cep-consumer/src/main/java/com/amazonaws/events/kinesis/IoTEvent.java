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

package com.amazonaws.events.kinesis;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * An IoT event class with simple attributes
 */

public class IoTEvent extends Event {
    int node, event, batch, infectedBy;
    String eventTime;
    double temperature;

    public int getNode() {
        return node;
    }

    public void setNode(int node) {
        this.node = node;
    }

    public int getEvent() {
        return event;
    }

    public void setEvent(int event) {
        this.event = event;
    }

    public int getBatch() {
        return batch;
    }

    public void setBatch(int batch) {
        this.batch = batch;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getInfectedBy() {
        return infectedBy;
    }

    public void setInfectedBy(int infectedBy) {
        this.infectedBy = infectedBy;
    }

    @Override
    public long getEventTimestamp() {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime dt = formatter.parseDateTime(this.getEventTime());

        return dt.getMillis();
    }

    @Override
    public String toString() {
        String iotEvent = "{"+
                "\"node\":"+node+","+
                "\"event\":"+event+","+
                "\"batch\":"+batch+","+
                "\"eventTime\":"+"\""+ eventTime +"\","+
                "\"temperature\":"+temperature+","+
                "\"infectedBy\":"+infectedBy+
                "}";
        return iotEvent;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof IoTEvent)) {
            return false;
        }

        IoTEvent ie = (IoTEvent) object;
        return (this.node == ie.node);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + this.node;
        return result;
    }
}