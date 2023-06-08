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
import software.amazon.awssdk.services.greengrassv2.model.CancelDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.DeleteDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.ValidationException;

import java.util.List;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface GreengrassDeploymentModel extends AWSResource<GreengrassV2Client> {
    Logger LOGGER = LogManager.getLogger(GreengrassDeployment.class);

    String deploymentId();

    @Nullable
    List<String> thingNames();

    @Override
    default void remove(GreengrassV2Client client) {
        try {
            client.cancelDeployment(CancelDeploymentRequest.builder()
                    .deploymentId(deploymentId())
                    .build());
        } catch (ValidationException ve) {
            LOGGER.warn("Could not cancel deployment {}", deploymentId());
        }
        try {
            client.deleteDeployment(DeleteDeploymentRequest.builder()
                    .deploymentId(deploymentId())
                    .build());
        } catch (ValidationException ve) {
            LOGGER.warn("Could not delete deployment {}", deploymentId());
        }
    }
}
