/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.DeleteComponentRequest;

@TestingModel
@Value.Immutable
interface GreengrassComponentModel extends AWSResource<GreengrassV2Client> {
    String componentName();

    String componentVersion();

    String componentArn();

    @Override
    default void remove(final GreengrassV2Client client) {
        client.deleteComponent(DeleteComponentRequest.builder()
                .arn(componentArn())
                .build());
    }
}
