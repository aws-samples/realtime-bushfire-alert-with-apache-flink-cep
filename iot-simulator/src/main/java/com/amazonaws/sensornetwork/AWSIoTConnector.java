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

package com.amazonaws.sensornetwork;

import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.utility.Args;

/**
 * A AWS IoT connector class to create an AWS IoT MQTT client and connect with the AWS endpoint
 */

public class AWSIoTConnector {

    static AWSIotMqttClient awsIotClient;
    static com.amazonaws.sensornetwork.AWSIoTUtil.KeyStorePasswordPair keyPair;

    /**
     * Reads and initialises the client configurations
     */
    public static void init() {
        keyPair = com.amazonaws.sensornetwork.AWSIoTUtil.getKeyStorePasswordPair(
                Args.certificateFile, Args.privateKeyFile);
    }

    /**
     * Initialises an AWS IoT MQTT client to transmit the sensor sensornetwork.events
     * to AWS IoT endpoint
     * @param clientId
     * @return
     */
    public static AWSIotMqttClient initClient(int clientId) {
        try {
            if (awsIotClient == null &&
                    Args.certificateFile != null &&
                    Args.privateKeyFile != null) {

                awsIotClient = new AWSIotMqttClient(
                        Args.clientEndpoint,
                        Integer.toString(clientId),
                        keyPair.keyStore,
                        keyPair.keyPassword);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (awsIotClient == null) {
            throw new IllegalArgumentException("Failed to construct AWS IoT client due to missing certificate.");
        }

        return awsIotClient;
    }
}
