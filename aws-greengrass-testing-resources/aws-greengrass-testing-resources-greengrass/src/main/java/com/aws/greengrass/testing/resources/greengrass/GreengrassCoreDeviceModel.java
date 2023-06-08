/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.DeleteCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GreengrassV2Exception;

@TestingModel
@Value.Immutable
interface GreengrassCoreDeviceModel extends AWSResource<GreengrassV2Client> {
    Logger LOGGER = LogManager.getLogger(GreengrassCoreDevice.class);

    String thingName();

    @Override
    default void remove(GreengrassV2Client client) {
        try {
            client.deleteCoreDevice(DeleteCoreDeviceRequest.builder()
                    .coreDeviceThingName(thingName())
                    .build());
        } catch (GreengrassV2Exception e) {
            LOGGER.warn("Could not delete core device {}", thingName(), e);
        }
    }
}
