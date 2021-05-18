package com.aws.greengrass.testing.resources.iam;


import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.iam.IamClient;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class IamRoleLifecycle extends AbstractAWSResourceLifecycle<IamClient> {
    @Inject
    public IamRoleLifecycle(IamClient client) {
        super(client, IamRoleSpec.class);
    }

    public IamRoleLifecycle() {
        this(IamClient.builder().build());
    }
}
