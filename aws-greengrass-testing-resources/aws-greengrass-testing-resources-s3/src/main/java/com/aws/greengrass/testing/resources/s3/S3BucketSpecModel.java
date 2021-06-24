/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.PutBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tagging;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface S3BucketSpecModel extends ResourceSpec<S3Client, S3Bucket>, S3TaggingMixin {
    String bucketName();

    @Nullable
    @Override
    S3Bucket resource();

    @Override
    default S3BucketSpec create(S3Client client, AWSResources resources) {
        CreateBucketResponse response = client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName())
                .build());
        client.putBucketTagging(PutBucketTaggingRequest.builder()
                .bucket(bucketName())
                .tagging(Tagging.builder()
                        .tagSet(convertTags(resources.generateResourceTags()))
                        .build())
                .build());
        return S3BucketSpec.builder()
                .from(this)
                .created(true)
                .resource(S3Bucket.builder()
                        .location(response.location())
                        .bucketName(bucketName())
                        .build())
                .build();
    }
}
