package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class GreengrassV2Lifecycle extends AbstractAWSResourceLifecycle<GreengrassV2Client> {
    @Inject
    public GreengrassV2Lifecycle(GreengrassV2Client client) {
        super(client, GreengrassComponentSpec.class, GreengrassDeploymentSpec.class);
    }

    public GreengrassV2Lifecycle() {
        this(GreengrassV2Client.create());
    }
}
