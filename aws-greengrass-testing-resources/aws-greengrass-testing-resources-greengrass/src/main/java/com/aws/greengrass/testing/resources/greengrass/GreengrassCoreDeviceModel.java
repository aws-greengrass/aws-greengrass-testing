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
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GreengrassV2Exception;
import software.amazon.awssdk.services.greengrassv2.model.ResourceNotFoundException;

@TestingModel
@Value.Immutable
interface GreengrassCoreDeviceModel extends AWSResource<GreengrassV2Client> {
    Logger LOGGER = LogManager.getLogger(GreengrassCoreDevice.class);

    int MAX_DELETE_ATTEMPTS = 3;

    String thingName();

    @Value.Default
    default long deleteDrainWaitMillis() {
        return 5000L;
    }

    // An in-flight fleet-status report can be ingested after DeleteCoreDevice and re-create the
    // record, so delete, wait for it to drain, then verify via GetCoreDevice and re-delete if needed.
    @Override
    default void remove(GreengrassV2Client client) {
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            try {
                client.deleteCoreDevice(DeleteCoreDeviceRequest.builder()
                        .coreDeviceThingName(thingName())
                        .build());
            } catch (GreengrassV2Exception e) {
                LOGGER.warn("Could not delete core device {}", thingName(), e);
            }

            try {
                Thread.sleep(deleteDrainWaitMillis());
                client.getCoreDevice(GetCoreDeviceRequest.builder()
                        .coreDeviceThingName(thingName())
                        .build());
            } catch (ResourceNotFoundException nfe) {
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (GreengrassV2Exception e) {
                LOGGER.warn("Could not verify deletion of core device {}; will retry", thingName(), e);
                continue;
            }
            // getCoreDevice returned normally — record is confirmed still present.
            LOGGER.info("Core device {} still present after delete attempt {} of {}; retrying",
                    thingName(), attempt, MAX_DELETE_ATTEMPTS);
        }
        LOGGER.warn("Core device {} still present after {} delete attempts", thingName(), MAX_DELETE_ATTEMPTS);
    }
}
