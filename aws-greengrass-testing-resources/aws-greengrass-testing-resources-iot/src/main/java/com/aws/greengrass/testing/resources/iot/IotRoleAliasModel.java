/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DeleteRoleAliasRequest;

@TestingModel
@Value.Immutable
interface IotRoleAliasModel extends AWSResource<IotClient> {
    String roleAlias();

    String roleAliasArn();

    @Override
    default void remove(IotClient client) {
        client.deleteRoleAlias(DeleteRoleAliasRequest.builder()
                .roleAlias(roleAlias())
                .build());
    }
}
