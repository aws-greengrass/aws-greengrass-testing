/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

@TestingModel
@Value.Immutable
interface S3BucketModel extends AWSResource<S3Client> {
    String location();

    String bucketName();

    @Override
    default void remove(S3Client client) {
        client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                .bucket(bucketName())
                .build()).contents().stream().map(s3 -> s3.key()).forEach(key -> {
                    client.deleteObject(DeleteObjectRequest.builder()
                            .key(key)
                            .bucket(bucketName())
                            .build());
                });
        client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(bucketName())
                .build());
    }
}
