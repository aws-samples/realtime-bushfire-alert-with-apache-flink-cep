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

package com.amazonaws.simulation;

import com.amazonaws.sensornetwork.*;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.utility.Args;
import com.beust.jcommander.JCommander;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * The main IoT simulator class
 */

public class IoTSimulator {
    // Logger initialization
    public final static Logger LOG = LogManager.getLogger(IoTSimulator.class);

    /**
     * Initializes the logger settings if debugging is enabled
     */
    static void init() {
        if(Args.debug) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            LoggerConfig lc = ctx.getConfiguration().getRootLogger();
            lc.setLevel(Level.DEBUG);
            ctx.updateLoggers();

            IoTSimulator.LOG.debug(ctx.getRootLogger().getLevel());
        }
    }

    /**
     * Main function
     * @param argv
     */
    public static void main(String ... argv) {
        Args args = new Args();

        JCommander jCommander = JCommander.newBuilder().addObject(args).build();
        jCommander.parse(argv);

        // Show help
        if (Args.help) {
            jCommander.usage();
            return;

        } else {
            // Initialization
            init();

            // Show command line arguments if debug option is enabled
            Args.show();

            // Initialises and starts the simulation
            AWSIoTConnector.init();
            IoTSimulator.init();

            AWSIotMqttClient awsIotMqttClient = null;

            String topic = Args.awsIotTopic;
            String payload;

            int batch = 0, event = 0, node = 0, infectedBy = 0;
            double temperature = 0.0;

            InputStream in = IoTSimulator.class.getResourceAsStream("/IoT.csv");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            // Reading the IoT streams from file
            try {
                String line = "";
                while ((line = br.readLine()) != null) {
                    String[] stream = line.split(",");

                    event = Integer.parseInt(stream[1]);
                    batch = Integer.parseInt(stream[0]);
                    node = Integer.parseInt(stream[2]);
                    temperature = Double.parseDouble(stream[3]);
                    infectedBy = Integer.parseInt(stream[4]);

                    // Generate and transmit the sensor events
                    payload = generateMetricEvent(batch, event, node, temperature, infectedBy);
                    awsIotMqttClient = AWSIoTConnector.initClient(node);
                    AWSIoTUtil.transmitIoTMessage(awsIotMqttClient, topic, payload);
                    IoTSimulator.LOG.debug(payload);

                    // Wait the metric interval period to generate a new batch
                    if (node == Args.sensors) {
                        // Sleep for the metric interval period
                        try {
                            TimeUnit.MINUTES.sleep(Args.metricInterval);
                        } catch (Exception e) {
                            IoTSimulator.LOG.debug(e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        IoTSimulator.LOG.info("Simulation of the IoT network of "+Args.sensors+" sensor nodes is completed.");
        System.exit(0);
    }

    private static String generateMetricEvent(int batch, int event, int node, double temperature, int infectedBy) {
        return ("{" +
                "\"batch\":"+batch+", " +
                "\"event\":"+event+", " +
                "\"node\":"+node+", " +
                "\"eventTimestamp\":"+System.currentTimeMillis()+", " +
                "\"temperature\":"+temperature+", " +
                "\"infectedBy\":"+infectedBy+
                "}");
    }
}
