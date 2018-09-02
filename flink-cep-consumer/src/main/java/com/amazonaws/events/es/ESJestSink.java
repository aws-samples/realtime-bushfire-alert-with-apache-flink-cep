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

package com.amazonaws.events.es;

import com.amazonaws.auth.AWS4Signer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.searchbox.client.JestResult;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A simple Elasticsearch Jest Sink class extending Apache Flink's RichSinkFunction and
 * based on https://github.com/aws-samples/flink-stream-processing-refarch/blob/master/flink-taxi-stream-processor/src/main/java/com/amazonaws/flink/refarch/utils/ElasticsearchJestSink.java
 * @param <T>
 */

public class ESJestSink<T> extends RichSinkFunction<T> implements CheckpointedFunction {
    private JestClient jestClient;
    private List<Index> documentBuffer;

    private final String indexName;
    private final String documentType;
    private final int batchSize;
    private final long maxBufferTime;
    private final Map<String, String> userConfig;

    private long lastBufferFlush;

    private static final String ES_SERVICE_NAME = "es";
    private static final boolean REQUEST_SENT_RETRY_ENABLED = true;
    private static final int RETRY_COUNT = 3;

    public ESJestSink(Map<String, String> config, String indexName, String documentType, int batchSize, long maxBufferTime) {
        this.userConfig = config;
        this.indexName = indexName;
        this.documentType = documentType;
        this.batchSize = batchSize;
        this.maxBufferTime = maxBufferTime;
        this.lastBufferFlush = System.currentTimeMillis();
    }

    @Override
    public void open(Configuration configuration) {
        ParameterTool pt = ParameterTool.fromMap(userConfig);

        AWS4Signer signer = new AWS4Signer();
        signer.setServiceName(ES_SERVICE_NAME);
        signer.setRegionName(pt.getRequired("region"));

        AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
        HttpRequestInterceptor requestInterceptor = new com.amazonaws.events.es.AWSSignerInterceptor(ES_SERVICE_NAME, signer, credentialsProvider);

        final JestClientFactory factory = new JestClientFactory() {
            @Override
            protected HttpClientBuilder configureHttpClient(final HttpClientBuilder builder) {
                builder.addInterceptorLast(requestInterceptor);
                builder.setRetryHandler(new DefaultHttpRequestRetryHandler(RETRY_COUNT, REQUEST_SENT_RETRY_ENABLED));
                return builder;
            }

            @Override
            protected HttpAsyncClientBuilder configureHttpClient(final HttpAsyncClientBuilder builder) {
                builder.addInterceptorLast(requestInterceptor);
                return builder;
            }

        };

        factory.setHttpClientConfig(new HttpClientConfig
                .Builder(pt.getRequired("es-endpoint"))
                .multiThreaded(true)
                .build());

        jestClient = factory.getObject();
        documentBuffer = new ArrayList<>(batchSize);
    }

    @Override
    public void invoke(T document, SinkFunction.Context context) {
        documentBuffer.add(new Index.Builder(document).index(indexName).type(documentType).build());

        if (documentBuffer.size() >= batchSize ||
                (System.currentTimeMillis() - lastBufferFlush) >= maxBufferTime) {
            try {
                flushDocumentBuffer();
            } catch (IOException e) {
                // If the request fails, then it will retry on the next invocation
                e.printStackTrace();
            }
        }
    }

    private void flushDocumentBuffer() throws IOException {
        Bulk.Builder bulkIndexBuilder = new Bulk.Builder();

        // Add all documents in the buffer to a bulk index action
        documentBuffer.forEach(bulkIndexBuilder::addAction);

        // Send the bulk index to Elasticsearch
        // FIXME: iterate through response and handle failures of single actions to obtain at least once semantics
        try {
            JestResult result = jestClient.execute(bulkIndexBuilder.build());

            if (!isValidResult(result)) {
                System.out.println(result.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        documentBuffer.clear();
        lastBufferFlush = System.currentTimeMillis();
    }

    private static boolean isValidResult(JestResult result) {
        Gson gson;

        if(!result.isSucceeded()) {
            gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(result.getErrorMessage()));

            return false;

        } else {
            gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(result.getJsonObject()));

            return true;
        }
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) throws Exception {
        do {
            try {
                flushDocumentBuffer();
            } catch (IOException e) {
                //if the request fails, that's fine, just retry on the next iteration
            }
        } while (! documentBuffer.isEmpty());
    }

    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        //nothing to initialize, as in flight documents are completely flushed during checkpointing
    }
}
