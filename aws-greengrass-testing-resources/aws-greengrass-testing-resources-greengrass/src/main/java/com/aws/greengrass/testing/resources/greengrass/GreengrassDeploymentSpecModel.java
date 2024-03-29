/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingGroupSpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;
import software.amazon.awssdk.services.greengrassv2.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.CreateDeploymentResponse;
import software.amazon.awssdk.services.greengrassv2.model.DeploymentIoTJobConfiguration;
import software.amazon.awssdk.services.greengrassv2.model.DeploymentPolicies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface GreengrassDeploymentSpecModel extends ResourceSpec<GreengrassV2Client, GreengrassDeployment> {
    @Nullable
    String thingArn();

    @Nullable
    String thingGroupArn();

    String deploymentName();

    @Nullable
    Map<String, ComponentDeploymentSpecification> components();

    @Nullable
    DeploymentIoTJobConfiguration deploymentJobConfiguration();

    @Nullable
    DeploymentPolicies deploymentPolicies();

    @Override
    @Nullable
    GreengrassDeployment resource();

    @Override
    default GreengrassDeploymentSpec create(GreengrassV2Client client, AWSResources resources) {
        AtomicReference<String> targetArn = new AtomicReference<>();
        final List<String> thingNames = new ArrayList<>();
        Optional.ofNullable(thingArn()).ifPresent(arn -> {
            targetArn.set(arn);
            resources.trackingSpecs(IotThingSpec.class)
                    .filter(thing -> thing.resource().thingArn().equals(arn))
                    .findFirst()
                    .ifPresent(thing -> {
                        thingNames.add(thing.thingName());
                    });
        });
        Optional.ofNullable(thingGroupArn()).ifPresent(arn -> {
            targetArn.set(arn);
            IotLifecycle lc = resources.lifecycle(IotLifecycle.class);
            resources.trackingSpecs(IotThingGroupSpec.class)
                    .filter(group -> group.resource().groupArn().equals(arn))
                    .findFirst()
                    .ifPresent(group -> {
                        lc.listThingsForGroup(group.groupName()).things().stream().forEach(thingNames::add);
                    });
        });

        CreateDeploymentResponse created = client.createDeployment(CreateDeploymentRequest.builder()
                .targetArn(targetArn.get())
                .deploymentName(deploymentName())
                .components(components())
                .deploymentPolicies(deploymentPolicies())
                .iotJobConfiguration(deploymentJobConfiguration())
                .tags(resources.generateResourceTags())
                .build());
        return GreengrassDeploymentSpec.builder()
                .from(this)
                .created(true)
                .resource(GreengrassDeployment.builder()
                        .deploymentId(created.deploymentId())
                        .addAllThingNames(thingNames)
                        .build())
                .build();
    }
}
