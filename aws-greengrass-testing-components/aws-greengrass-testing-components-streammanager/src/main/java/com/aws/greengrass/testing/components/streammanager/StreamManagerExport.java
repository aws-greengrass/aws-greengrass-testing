package com.aws.greengrass.testing.components.streammanager;

import com.amazonaws.greengrass.streammanager.client.exception.StreamManagerException;
import com.amazonaws.greengrass.streammanager.model.S3ExportTaskDefinition;
import com.amazonaws.greengrass.streammanager.model.Status;
import com.amazonaws.greengrass.streammanager.model.StatusMessage;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class StreamManagerExport {

    /**
     * This component entry serves a single purpose: Upload a file to S3.
     *
     * @param args ignored input args
     * @throws StreamManagerException failing to initialize the upload
     * @throws JsonProcessingException failing to serialize the payload
     * @throws ExecutionException failing to complete the upload
     */
    public static void main(String[] args) throws StreamManagerException, JsonProcessingException, ExecutionException {
        StreamManagerExportComponent component = DaggerStreamManagerExportComponent.create();

        final String bucketName = System.getProperty("s3.bucketName");
        final String key = System.getProperty("key");
        final String file = System.getProperty("file.input");
        final CompletableFuture<StatusMessage> result = component.s3().export(new S3ExportTaskDefinition()
                .withBucket(bucketName)
                .withKey(key)
                .withInputUrl(file));
        try {
            final StatusMessage message = result.get();
            if (message.getStatus().equals(Status.Success)) {
                System.out.println(String.format("Uploaded %s to s3://%s/%s", file, bucketName, key));
            } else {
                throw new RuntimeException("Failed to upload " + file + ": " + message.getMessage());
            }
        } catch (InterruptedException e) {
            System.out.println("Upload was interrupted");
        }
    }
}
