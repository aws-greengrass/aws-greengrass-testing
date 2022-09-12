/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.immutables.value.Value;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface S3ObjectSpecModel extends ResourceSpec<S3Client, S3Object>, S3TaggingMixin {
    String key();

    String bucket();

    Path content();

    @Nullable
    @Override
    S3Object resource();

    //Default size in bytes (5MB)
    int DEFAULT_MULTIPART_SIZE = 1024 * 1024 * 5;
    Logger LOGGER = LogManager.getLogger(AbstractAWSResourceLifecycle.class);

    @Override
    default S3ObjectSpec create(S3Client client, AWSResources resources) {
        return this.create(client, resources, 0);
    }

    default S3ObjectSpec create(S3Client client, AWSResources resources, int retryCount) {
        final CreateMultipartUploadRequest multipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket())
                .key(key())
                .tagging(Tagging.builder()
                        .tagSet(convertTags(resources.generateResourceTags()))
                        .build())
                .build();

        final CreateMultipartUploadResponse multipartUploadResponse = client
                .createMultipartUpload(multipartUploadRequest);

        List<CompletedPart> parts = new ArrayList<>();
        FileInputStream fileInputStream = null;
        byte[] buffer = new byte[DEFAULT_MULTIPART_SIZE];
        int len = 0;
        int partNumber = 1;
        try {
            if (Files.exists(content())) {
                fileInputStream = new FileInputStream(content().toFile());
            } else {
                String errMsg = "Caught exception while uploading artifacts to S3: File does not exist on path "
                        + content();
                LOGGER.error(errMsg);
                throw new RuntimeException(errMsg);
            }
            while ((len = fileInputStream.read(buffer)) != -1) {
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket())
                        .key(key())
                        .uploadId(multipartUploadResponse.uploadId())
                        .partNumber(partNumber)
                        .contentLength((long) len)
                        .build();

                UploadPartResponse uploadPartResponse = client.uploadPart(uploadPartRequest,
                        RequestBody.fromBytes(buffer));

                parts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build());

                partNumber++;
            }
        } catch (IOException e) {
            LOGGER.error("IOException occurred while uploading artifacts to S3 bucket: {}", e);
            throw new RuntimeException(e);
        } catch (SdkClientException e) {
            if (retryCount > 3) {
                throw e;
            }
            LOGGER.debug("S3 upload request threw an SdkClientException, retied {} times...", retryCount);
            return create(client, resources, retryCount + 1);
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                LOGGER.error("IOException occurred while closing fileInputStream {}", e);
                throw new RuntimeException(e);
            }
        }

        CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucket())
                .key(key())
                .uploadId(multipartUploadResponse.uploadId())
                .multipartUpload(CompletedMultipartUpload
                        .builder()
                        .parts(parts)
                        .build())
                .build();

        CompleteMultipartUploadResponse completeMultipartUploadResponse = client
                .completeMultipartUpload(completeMultipartUploadRequest);

        return S3ObjectSpec.builder()
                .from(this)
                .created(true)
                .resource(S3Object.builder()
                        .bucket(bucket())
                        .key(key())
                        .etag(completeMultipartUploadResponse.eTag())
                        .versionId(completeMultipartUploadResponse.versionId())
                        .build())
                .build();
    }
}
