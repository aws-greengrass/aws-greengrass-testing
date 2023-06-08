/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVersionListItem;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVisibilityScope;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceResponse;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentResponse;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentVersionsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListEffectiveDeploymentsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentVersionsIterable;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentsIterable;
import software.amazon.awssdk.services.greengrassv2.paginators.ListEffectiveDeploymentsIterable;

import java.util.Optional;
import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class GreengrassV2Lifecycle extends AbstractAWSResourceLifecycle<GreengrassV2Client> {
    @Inject
    public GreengrassV2Lifecycle(GreengrassV2Client client) {
        super(client, GreengrassComponentSpec.class, GreengrassDeploymentSpec.class, GreengrassCoreDeviceSpec.class);
    }

    public GreengrassV2Lifecycle() {
        this(GreengrassV2Client.create());
    }

    /**
     * Attempts to obtain the mapped Greengrass core device to IoT core.
     *
     * @param thingName IoT core thing name
     * @return
     */
    public Optional<GetCoreDeviceResponse> coreDevice(String thingName) {
        try {
            return Optional.ofNullable(client.getCoreDevice(GetCoreDeviceRequest.builder()
                    .coreDeviceThingName(thingName)
                    .build()));
        } catch (ResourceNotFoundException nfe) {
            return Optional.empty();
        }
    }

    /**
     * List effective deployments performed on the device by thing name.
     *
     * @param thingName Name of the core device. This matches the thing name used in IoT core.
     * @return
     */
    public ListEffectiveDeploymentsIterable listDeviceDeployments(String thingName) {
        return client.listEffectiveDeploymentsPaginator(ListEffectiveDeploymentsRequest.builder()
                .coreDeviceThingName(thingName)
                .build());
    }

    /**
     * Get a Greengrass deployment by ID.
     *
     * @param deploymentId ID of the Greengrass deployment
     * @return
     */
    public GetDeploymentResponse deployment(String deploymentId) {
        return client.getDeployment(GetDeploymentRequest.builder()
                .deploymentId(deploymentId)
                .build());
    }

    /**
     * List all component versions by fully qualified component ARN.
     *
     * @param arn Fully qualified AWS ARN
     * @return
     */
    public ListComponentVersionsIterable listComponentVersions(String arn) {
        return client.listComponentVersionsPaginator(ListComponentVersionsRequest.builder()
                .arn(arn)
                .build());
    }

    /**
     * Grabs the latest version of the Component by ARN.
     *
     * @param arn String AWS ARN of the Greengrass Component
     * @return
     */
    public Optional<ComponentVersionListItem> latestVersionFor(String arn) {
        return client.listComponentVersions(ListComponentVersionsRequest.builder()
                .arn(arn)
                .maxResults(1)
                .build())
                .componentVersions()
                .stream()
                .findFirst();
    }

    /**
     * List components by {@link ComponentVisibilityScope}.
     *
     * @param scope List all of the components by {@link ComponentVisibilityScope}
     * @return
     */
    public ListComponentsIterable listComponents(ComponentVisibilityScope scope) {
        return client.listComponentsPaginator(ListComponentsRequest.builder()
                .scope(scope)
                .build());
    }
}
