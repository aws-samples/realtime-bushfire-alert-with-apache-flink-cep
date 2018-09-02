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

package com.amazonaws.utility;

import com.amazonaws.simulation.IoTSimulator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

/**
 * A class that defines the required and optional command line parameters
 * for using with the JCommander library
 */

public class Args {

    //Command line options
    // IoT network specific
    @Parameter(names={"--sensors", "-s"}, validateWith = PositiveInteger.class, description = "Sensor count: <number>")
    public static int sensors = 11; // Default 11 IoT sensor nodes as per the blog article

    // Metric generation specific
    @Parameter(names={"--interval", "-i"}, validateWith = PositiveInteger.class, description = "Metric interval period in minute: <number>")
    public static long metricInterval = 1; // Default 1 m

    // AWS IoT specific
    @Parameter(names={"--aws-iot-endpoint", "-endpoint"}, required = true, description = "AWS IoT endpoint: <endpoint-url>")
    public static String clientEndpoint;
    @Parameter(names={"--aws-iot-topic", "-topic"}, required = true, description = "AWS IoT topic name: <topic-name>")
    public static String awsIotTopic;
    @Parameter(names={"--certificate", "-cert"}, required = true, description = "AWS IoT client certificate: <certificate-filename>")
    public static String certificateFile;
    @Parameter(names={"--private-key", "-pk"}, required = true, description = "AWS IoT client private key: <key-filename>")
    public static String privateKeyFile;

    // Others
    @Parameter(names={"--debug", "-d"}, description = "Enable debug logs: [true, false]")
    public static boolean debug = false;
    @Parameter(names={"--help", "-h"}, help = true, description = "Help")
    public static boolean help = false;

    /**
     * Prints the command line arguments if Logger debug option is enabled
     */
    public static void show() {
        IoTSimulator.LOG.debug("Listing all of the command line parameters:"
                +" --sensors "+sensors
                +" --interval "+metricInterval
                +" --aws-iot-endpoint "+clientEndpoint
                +" --aws-iot-topic "+awsIotTopic
                +" --certificate "+certificateFile
                +" --private-key "+privateKeyFile);
    }
}
