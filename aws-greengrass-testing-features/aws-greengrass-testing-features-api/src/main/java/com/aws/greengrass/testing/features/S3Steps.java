/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.s3.S3BucketSpec;
import com.aws.greengrass.testing.resources.s3.S3Lifecycle;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class S3Steps {
    private static final String DEFAULT_BUCKET = "gg-component-store";
    private final AWSResources resources;
    private final TestId testId;

    @Inject
    S3Steps(
            final AWSResources resources,
            final TestId testId) {
        this.resources = resources;
        this.testId = testId;
    }

    @When("I create an S3 bucket for testing")
    public void createTestingBucket() {
        createS3Bucket(DEFAULT_BUCKET);
    }

    /**
     * Create an S3 bucket based on a name.
     *
     * @param bucketName Name of the S3 bucket
     */
    @When("I create an S3 bucket named {word}")
    public void createS3Bucket(String bucketName) {
        resources.create(S3BucketSpec.builder()
                .bucketName(testId.idFor(bucketName))
                .build());
    }

    @Then("the S3 bucket contains the key {word}")
    public void bucketContainsKey(String key) {
        bucketContainsKey(DEFAULT_BUCKET, key);
    }

    /**
     * Step that checks if a object exists in a bucket.
     *
     * @param bucketName name of the bucket
     * @param key name of the object key that should exist
     */
    @Then("the S3 bucket named {word} contains the key {word}")
    public void bucketContainsKey(String bucketName, String key) {
        S3Lifecycle s3 = resources.lifecycle(S3Lifecycle.class);
        assertTrue(s3.objectExists(testId.idFor(bucketName), key),
                "The object " + key + " does not exist in " + bucketName);
    }
}