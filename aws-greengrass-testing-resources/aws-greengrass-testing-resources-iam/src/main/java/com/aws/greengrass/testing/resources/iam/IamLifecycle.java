/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iam;


import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;

import java.util.Optional;
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

    /**
     * Get the IamRole for given name.
     * @param iamRoleName role name
     * @return {@link IamRole}
     */
    public Optional<IamRole> getIamRole(String iamRoleName) {
        try {
            GetRoleResponse response = this.client.getRole(GetRoleRequest.builder().roleName(iamRoleName).build());
            return Optional.of(IamRole.builder()
                    .roleName(response.role().roleName())
                    .roleArn(response.role().arn())
                    .build());
        } catch (NoSuchEntityException e) {
            return Optional.empty();
        }
    }
}
