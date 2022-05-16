/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface S3ObjectSpecModel extends ResourceSpec<S3Client, S3Object>, S3TaggingMixin {
    String key();

    String bucket();

    RequestBody content();

    @Nullable
    @Override
    S3Object resource();

    //Default size in bytes (5MB)
    int DEFAULT_MULTIPART_SIZE = 1024 * 1024 * 5;

    @Override
    default S3ObjectSpec create(S3Client client, AWSResources resources) {
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
        InputStream inputDataStream = content().contentStreamProvider().newStream();
        ByteArrayOutputStream outputDataStream = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[DEFAULT_MULTIPART_SIZE];
            int len = 0;
            int partNumber = 1;
            while ((len = inputDataStream.read(buffer)) != -1) {
                outputDataStream.write(buffer, 0, len);
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket())
                        .key(key())
                        .uploadId(multipartUploadResponse.uploadId())
                        .partNumber(partNumber)
                        .contentLength((long)outputDataStream.size())
                        .build();

                UploadPartResponse uploadPartResponse = client.uploadPart(uploadPartRequest,
                        RequestBody.fromBytes(outputDataStream.toByteArray()));

                parts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build());

                outputDataStream.reset();
                partNumber++;
            }
        } catch (IOException e) {
            System.out.println("IOException occurred while uploading artifacts to S3 bucket" + e);
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
