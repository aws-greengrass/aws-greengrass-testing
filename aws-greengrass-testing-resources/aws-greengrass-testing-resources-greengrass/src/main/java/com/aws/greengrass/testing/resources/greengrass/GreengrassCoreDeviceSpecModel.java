/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface GreengrassCoreDeviceSpecModel extends ResourceSpec<GreengrassV2Client, GreengrassCoreDevice> {
    @Nullable
    String thingName();

    @Override
    @Nullable
    GreengrassCoreDevice resource();

    @Override
    default GreengrassCoreDeviceSpec create(GreengrassV2Client client, AWSResources resources) {
        return GreengrassCoreDeviceSpec.builder()
                .from(this)
                .created(true)
                .resource(GreengrassCoreDevice.builder()
                        .thingName(thingName())
                        .build())
                .build();
    }
}
