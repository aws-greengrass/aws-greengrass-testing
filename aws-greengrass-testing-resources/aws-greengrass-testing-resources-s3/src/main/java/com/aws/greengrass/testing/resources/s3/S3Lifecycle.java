/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class S3Lifecycle extends AbstractAWSResourceLifecycle<S3Client> {
    @Inject
    public S3Lifecycle(S3Client client) {
        super(client, S3ObjectSpec.class, S3BucketSpec.class);
    }

    public S3Lifecycle() {
        this(S3Client.create());
    }

    /**
     * Checks if the bucket exists by name.
     *
     * @param bucketName Name of the bucket to check
     * @return
     */
    public boolean bucketExists(String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    /**
     * Checks if the object exists in the bucket.
     *
     * @param bucketName name of the bucket to check
     * @param key name of the key to check
     * @return
     */
    public boolean objectExists(String bucketName, String key) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
