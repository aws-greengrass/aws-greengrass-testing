/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iam;


import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.iam.IamClient;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class IamLifecycle extends AbstractAWSResourceLifecycle<IamClient> {
    @Inject
    public IamLifecycle(IamClient client) {
        super(client, IamPolicySpec.class, IamRoleSpec.class);
    }

    public IamLifecycle() {
        this(IamClient.builder().build());
    }
}
