package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.s3.S3BucketSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.When;

import javax.inject.Inject;

@ScenarioScoped
public class S3Steps {
    private final AWSResources resources;
    private final TestId testId;

    @Inject
    public S3Steps(
            final AWSResources resources,
            final TestId testId) {
        this.resources = resources;
        this.testId = testId;
    }

    @When("I create an S3 bucket for testing")
    public void createTestingBucket() {
        createS3Bucket("gg-component-store");
    }

    @When("I create an S3 bucket named {word}")
    public void createS3Bucket(String bucketName) {
        resources.create(S3BucketSpec.builder()
                .bucketName(testId.idFor(bucketName))
                .build());
    }
}