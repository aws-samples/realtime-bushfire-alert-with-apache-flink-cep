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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;

import org.joda.time.DateTime;

/**
 * A simple Event class which parses incoming records into class object from JSON
 */

public abstract class Event {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (
                    json, typeOfT, context) -> new DateTime(json.getAsString()))
            .create();

    public static Event parseEvent(byte[] stream) {
        JsonReader jsonReader =  new JsonReader(new InputStreamReader(new ByteArrayInputStream(stream)));
        return gson.fromJson(Streams.parse(jsonReader), IoTEvent.class);
    }

    public abstract long getEventTimestamp();
}
