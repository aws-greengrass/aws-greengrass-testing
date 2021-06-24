/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest;

@TestingModel
@Value.Immutable
interface IamRoleModel extends AWSResource<IamClient> {
    String roleArn();

    String roleName();

    @Override
    default void remove(IamClient client) {
        for (AttachedPolicy policy : client.listAttachedRolePolicies(ListAttachedRolePoliciesRequest.builder()
                .roleName(roleName())
                .build()).attachedPolicies()) {
            client.detachRolePolicy(DetachRolePolicyRequest.builder()
                    .roleName(roleName())
                    .policyArn(policy.policyArn())
                    .build());
        }
        client.deleteRole(DeleteRoleRequest.builder()
                .roleName(roleName())
                .build());
    }
}
