package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class S3Lifecycle extends AbstractAWSResourceLifecycle<S3Client> {
    @Inject
    public S3Lifecycle(S3Client client) {
        super(client, S3ObjectSpec.class);
    }

    public S3Lifecycle() {
        this(S3Client.create());
    }
}
