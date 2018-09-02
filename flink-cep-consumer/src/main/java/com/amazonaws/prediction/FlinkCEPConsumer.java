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

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.events.es.ESJestSink;
import com.amazonaws.events.flink.TimeLagWatermarkGenerator;
import com.amazonaws.events.kinesis.Event;
import com.amazonaws.events.kinesis.EventSchema;
import com.amazonaws.events.kinesis.IoTEvent;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.google.common.collect.ImmutableMap;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import java.util.*;

/**
 * The main class for the Apache Flink's CEP based consumer
 */

public class FlinkCEPConsumer {
    // Static parameters
    private static final String ES_DEFAULT_INDEX = "sensor-readings";
    private static final String ES_DEFAULT_INDEX_TYPE = "sensor";
    private static final String ES_DEFAULT_BATCH_SIZE = "100";  // Bulk document batch size
    private static final long ES_DEFAULT_BATCH_DURATION = 60000; // Bulk flush interval set to 60 s

    // AWS SNS client builder parameters
    private static AmazonSNSClientBuilder snsClientBuilder = AmazonSNSClientBuilder.standard()
                    .withCredentials(new DefaultAWSCredentialsProviderChain());

    private static AmazonSNSClient snsClient = (AmazonSNSClient) snsClientBuilder.build();

    /**
     * Main entry function
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Read the command line arguments
        ParameterTool pt = ParameterTool.fromArgs(args);

        // Create the Flink Stream execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(5000); // checkpoint every 5000 ms
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        // Set Kinesis Stream specific parameters
        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, pt.getRequired("region"));
        kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.DEFAULT_STREAM_INITIAL_POSITION, "LATEST");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_MAX, "10000");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_BACKOFF_BASE, "500");
        kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "200");

        // Consume the data streams from AWS Kinesis stream
        DataStream<Event> dataStream = env.addSource(new FlinkKinesisConsumer<>(
                pt.getRequired("stream"),
                new EventSchema(),
                kinesisConsumerConfig))
                .name("Kinesis Stream Consumer");

        DataStream<Event> kinesisStream = dataStream
                .assignTimestampsAndWatermarks(new TimeLagWatermarkGenerator())
                .map(event -> (IoTEvent) event);

        // Prints the mapped records from the Kinesis stream
        kinesisStream.print();

        // Define the pattern to be matched within the incoming stream
        Pattern<Event, ?> pattern = Pattern
                .<Event> begin("node1").subtype(IoTEvent.class).where(new TemperaturePatternCondition())
                .followedBy("node2").subtype(IoTEvent.class).where(new InfectionPatternCondition("node1"))
                .followedBy("node3").subtype(IoTEvent.class).where(new InfectionPatternCondition("node2"))
                .followedBy("node4").subtype(IoTEvent.class).where(new InfectionPatternCondition("node3"))
                .followedBy("node5").subtype(IoTEvent.class).where(new InfectionPatternCondition("node4"))
                .within(Time.seconds(60));  // Pattern matches within 60 s

        // Match the pattern in the input data stream
        PatternStream<Event> patternStream = CEP.pattern(kinesisStream, pattern);

        // Detects pattern match and alert through SNS
        DataStream<Alert> alerts = patternStream.select(
                new PatternSelectFunction<Event, Alert>() {
                    @Override
                    public Alert select(Map<String, List<Event>> pattern) throws Exception {
                        Alert alert = new Alert(pattern);
                        snsClient.publish(pt.getRequired("sns-topic-arn"), alert.toString());

                        return alert;
                    }
        }).name("Bushfire Alert Sink");

        // Prints the generated alerts in STDOUT
        alerts.print();

        // Ingest the incoming stream to Elasticsearch by adding a sink to the stream
        if (pt.has("es-endpoint")) {
            final String indexName = pt.get("es-index", ES_DEFAULT_INDEX);
            final String indexType = pt.get("es-index-type", ES_DEFAULT_INDEX_TYPE);
            final String batchSize = pt.get("es-batch-size", ES_DEFAULT_BATCH_SIZE);

            final ImmutableMap<String, String> config = ImmutableMap.<String, String>builder()
                    .put("es-endpoint", pt.getRequired("es-endpoint"))
                    .put("region", pt.getRequired("region"))
                    .build();

            kinesisStream.addSink(new ESJestSink<>(config, indexName, indexType, Integer.parseInt(batchSize), ES_DEFAULT_BATCH_DURATION))
                    .name("Elasticsearch Sink");
        }

        // Execute the environment
        env.execute("Real-time Bushfire Prediction Example");
    }
}

/**
 * Simple temperature pattern condition
 */
class TemperaturePatternCondition extends SimpleCondition<IoTEvent> {
    @Override
    public boolean filter(IoTEvent ioTEvent) {
        return ioTEvent.getTemperature() >= PatternConstants.TEMPERATURE_THRESHOLD;
    }
}

/**
 * Iterative infection pattern condition
 */
class InfectionPatternCondition extends IterativeCondition<IoTEvent> {
    String node = null;

    InfectionPatternCondition(String n){
        node = n;
    }

    @Override
    public boolean filter(IoTEvent ioTEvent, Context<IoTEvent> ctx) throws Exception {

        for (IoTEvent infectedIoTEvent : ctx.getEventsForPattern(node)) {
            if(ioTEvent.getTemperature() >= PatternConstants.TEMPERATURE_THRESHOLD
                    && ioTEvent.getInfectedBy() == infectedIoTEvent.getNode())
                return true;
        }

        return false;
    }
}

/**
 * Pattern constants and threshold values
 */
class PatternConstants {
    public static final double TEMPERATURE_THRESHOLD = 50.0;
}
