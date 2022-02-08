/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import com.aws.greengrass.testing.resources.iam.IamRole;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasRequest;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;
import software.amazon.awssdk.services.iot.model.DescribeRoleAliasRequest;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotRoleAliasSpecModel extends ResourceSpec<IotClient, IotRoleAlias>, IotTaggingMixin {
    String name();

    IamRole iamRole();

    @Override
    default IotRoleAliasSpec create(IotClient client, AWSResources resources) {
        CreateRoleAliasResponse createdAlias = client.createRoleAlias(CreateRoleAliasRequest.builder()
                .roleAlias(name())
                .roleArn(iamRole().roleArn())
                .tags(convertTags(resources.generateResourceTags()))
                .build());
        return IotRoleAliasSpec.builder()
                .from(this)
                .created(true)
                .resource(IotRoleAlias.builder()
                        .roleAlias(createdAlias.roleAlias())
                        .roleAliasArn(createdAlias.roleAliasArn())
                        .build())
                .build();
    }

    @Override
    default boolean availableInCloud(IotClient client) {
        try {
            client.describeRoleAlias(DescribeRoleAliasRequest.builder()
                    .roleAlias(name())
                    .build());
        } catch (ResourceNotFoundException e) {
            return false;
        }
        return true;
    }

    @Nullable
    IotRoleAlias resource();
}
