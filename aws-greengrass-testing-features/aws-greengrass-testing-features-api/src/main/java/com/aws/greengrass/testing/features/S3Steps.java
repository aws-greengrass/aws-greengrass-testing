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

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;


@ScenarioScoped
public class S3Steps {
    private static final String DEFAULT_BUCKET = "gg-component-store";
    private final AWSResources resources;
    private final TestId testId;
    private final WaitSteps waits;

    @Inject
    S3Steps(
            final AWSResources resources,
            final TestId testId,
            final WaitSteps waits) {
        this.resources = resources;
        this.testId = testId;
        this.waits = waits;
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

    @Then("the S3 bucket contains the key {word} within {int} {word}")
    public void bucketContainsKey(String key, int value, String unit) throws InterruptedException {
        bucketContainsKey(DEFAULT_BUCKET, key, value, unit);
    }

    /**
     * Step that checks if a object exists in a bucket.
     *
     * @param bucketName name of the bucket
     * @param key name of the object key that should exist
     * @param value value for wait duration
     * @param unit duration for waiting
     * @throws InterruptedException thread interrupted while waiting
     * @throws IllegalStateException thrown when the bucket does not contain the object
     */
    @Then("the S3 bucket named {word} contains the key {word} within {int} {word}")
    public void bucketContainsKey(String bucketName, String key, int value, String unit) throws InterruptedException {
        S3Lifecycle s3 = resources.lifecycle(S3Lifecycle.class);
        if (!waits.untilTrue(() -> s3.objectExists(testId.idFor(bucketName), key),
                value, TimeUnit.valueOf(unit.toUpperCase()))) {
            throw new IllegalStateException("The object " + key + " does not exist in " + bucketName);
        }
    }
}