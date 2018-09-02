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

import com.amazonaws.services.iot.client.AWSIotConnectionStatus;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.simulation.IoTSimulator;

import java.io.*;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;

/**
 * A simple AWS IoT utility class based on https://github.com/aws/aws-iot-device-sdk-java/blob/master/aws-iot-device-sdk-java-samples/src/main/java/com/amazonaws/services/iot/client/sample/sampleUtil/SampleUtil.java
 */

public class AWSIoTUtil {

    public static class KeyStorePasswordPair {
        public KeyStore keyStore;
        public String keyPassword;

        public KeyStorePasswordPair(KeyStore keyStore, String keyPassword) {
            this.keyStore = keyStore;
            this.keyPassword = keyPassword;
        }
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(
            final String certificateFile, final String privateKeyFile) {

        return getKeyStorePasswordPair(certificateFile, privateKeyFile, null);
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(
            final String certificateFile, final String privateKeyFile, String keyAlgorithm) {

        /*IoTSimulator.LOG.debug("Certificate file: "+ certificateFile
                +"\nPrivate key file: "+ privateKeyFile);*/

        final PrivateKey privateKey = loadPrivateKeyFromFile(privateKeyFile, keyAlgorithm);
        final List<Certificate> certChain = loadCertificatesFromFile(certificateFile);

        if (certChain == null || privateKey == null)
            return null;

        return getKeyStorePasswordPair(certChain, privateKey);
    }

    public static KeyStorePasswordPair getKeyStorePasswordPair(final List<Certificate> certificates, final PrivateKey privateKey) {
        KeyStore keyStore;
        String keyPassword;

        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);

            // Randomly generated key password for the key in the KeyStore
            keyPassword = new BigInteger(128, new SecureRandom()).toString(32);

            Certificate[] certChain = new Certificate[certificates.size()];
            certChain = certificates.toArray(certChain);
            keyStore.setKeyEntry("alias", privateKey, keyPassword.toCharArray(), certChain);

        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            IoTSimulator.LOG.error("Failed to create key store");
            return null;
        }

        return new KeyStorePasswordPair(keyStore, keyPassword);
    }

    private static List<Certificate> loadCertificatesFromFile(final String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            IoTSimulator.LOG.error("Certificate file " + filename + " is missing.");
            return null;
        }

        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (List<Certificate>) certFactory.generateCertificates(stream);

        } catch (IOException | CertificateException e) {
            IoTSimulator.LOG.error("Failed to load certificate from " + filename);
        }

        return null;
    }

    private static PrivateKey loadPrivateKeyFromFile(final String filename, final String algorithm) {
        PrivateKey privateKey = null;
        File file = new File(filename);

        if (!file.exists()) {
            IoTSimulator.LOG.error("Private key file " + filename + " is missing.");
            return null;
        }

        try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
            privateKey = com.amazonaws.sensornetwork.PrivateKeyReader.getPrivateKey(stream, algorithm);
        } catch (Exception e) {
            IoTSimulator.LOG.error("Failed to load private key from " + filename);
        }

        return privateKey;
    }


    /**
     * Transmits the generated sensor network events to AWS IoT endpoint
     * @param awsIotClient
     * @param topic
     * @param payload
     */
    public static void transmitIoTMessage(AWSIotMqttClient awsIotClient, String topic, String payload) {
        try {
            if(awsIotClient.getConnectionStatus() == AWSIotConnectionStatus.DISCONNECTED)
                awsIotClient.connect();

            awsIotClient.publish(topic, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}