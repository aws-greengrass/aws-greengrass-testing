/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.components.streammanager;

import com.amazonaws.greengrass.streammanager.client.StreamManagerClient;
import com.amazonaws.greengrass.streammanager.client.exception.StreamManagerException;
import com.amazonaws.greengrass.streammanager.client.utils.ValidateAndSerialize;
import com.amazonaws.greengrass.streammanager.model.Message;
import com.amazonaws.greengrass.streammanager.model.MessageStreamDefinition;
import com.amazonaws.greengrass.streammanager.model.ReadMessagesOptions;
import com.amazonaws.greengrass.streammanager.model.S3ExportTaskDefinition;
import com.amazonaws.greengrass.streammanager.model.Status;
import com.amazonaws.greengrass.streammanager.model.StatusConfig;
import com.amazonaws.greengrass.streammanager.model.StatusLevel;
import com.amazonaws.greengrass.streammanager.model.StatusMessage;
import com.amazonaws.greengrass.streammanager.model.StrategyOnFull;
import com.amazonaws.greengrass.streammanager.model.export.ExportDefinition;
import com.amazonaws.greengrass.streammanager.model.export.S3ExportTaskExecutorConfig;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class S3Export {
    private final StreamManagerClient client;

    @Inject
    public S3Export(final StreamManagerClient client) {
        this.client = client;
    }

    /**
     * Will stream upload a local file to S3 via the specified {@link S3ExportTaskDefinition}.
     *
     * @param streamName contains the name of the export stream in StreamManager
     * @param definition contains the fully qualified {@link S3ExportTaskDefinition}
     * @return immediately retuns a {@link CompletableFuture} eventually containing a terminal {@link StatusMessage}
     * @throws StreamManagerException any failure to initialize the stream upload
     * @throws JsonProcessingException any JSON serialization failure
     */
    public CompletableFuture<StatusMessage> export(String streamName, S3ExportTaskDefinition definition)
            throws StreamManagerException, JsonProcessingException {
        ExportDefinition exports = new ExportDefinition()
                .withS3TaskExecutor(Collections.singletonList(
                        new S3ExportTaskExecutorConfig().withIdentifier("S3Export" + streamName).withStatusConfig(
                                new StatusConfig().withStatusLevel(StatusLevel.INFO)
                                        .withStatusStreamName(streamName + "Status"))));

        client.createMessageStream(new MessageStreamDefinition()
                .withName(streamName + "Status")
                .withStrategyOnFull(StrategyOnFull.OverwriteOldestData));

        client.createMessageStream(new MessageStreamDefinition()
                .withName(streamName)
                .withStrategyOnFull(StrategyOnFull.OverwriteOldestData)
                .withExportDefinition(exports));

        client.appendMessage(streamName, ValidateAndSerialize.validateAndSerializeToJsonBytes(definition));

        return CompletableFuture.supplyAsync(() -> terminalStatus(streamName));
    }

    private StatusMessage terminalStatus(final String streamName) {
        while (true) {
            try {
                List<Message> messages = client.readMessages(streamName + "Status", new ReadMessagesOptions()
                        .withMaxMessageCount(1L)
                        .withReadTimeoutMillis(1000L));
                Set<Status> terminalStatus = new HashSet<Status>() {{
                    add(Status.Failure);
                    add(Status.Canceled);
                    add(Status.Success);
                }};
                for (Message message : messages) {
                    StatusMessage statusMessage = ValidateAndSerialize.deserializeJsonBytesToObj(
                            message.getPayload(), StatusMessage.class);
                    if (terminalStatus.contains(statusMessage.getStatus())) {
                        return statusMessage;
                    }
                }
            } catch (StreamManagerException e) {
                System.err.println("Error occurred when reading " + streamName + "Status. Trying again.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5L));
            } catch (InterruptedException e) {
                // Was interrupted... just break this loop and bail
                break;
            }
        }
        throw new RuntimeException("Failed to finish the upload.");
    }
}
