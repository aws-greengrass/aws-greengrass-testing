/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.google.inject.Inject;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;
import software.amazon.awssdk.services.greengrassv2.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.CreateDeploymentResponse;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentResponse;
import software.amazon.awssdk.services.iot.model.GroupNameAndArn;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ScenarioScoped
public class AWSResourcesSteps implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger(AWSResourcesSteps.class);
    private final AWSResources resources;
    private final TestContext testContext;

    @Inject
    public AWSResourcesSteps(
            final AWSResources resources,
            final TestContext testContext) {
        this.resources = resources;
        this.testContext = testContext;
    }

    @After(order = 11000)
    @Override
    public void close() throws IOException {
        if (testContext.initializationContext().persistInstalledSoftware()) {
            cleanupComponents();
        }
        resources.close();
        testContext.close();
        LOGGER.info("Successfully removed externally created resources");
    }

    @SuppressWarnings("MissingJavadocMethod")
    public void cleanupComponents() {
        IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
        IotThing thing = lifecycle.thingByThingName(testContext.coreThingName());
        emptyDeployment(thing.thingArn());

        SdkIterable<GroupNameAndArn> thingGroupIterable =
                lifecycle.listThingGroupsForAThing(testContext.coreThingName()).thingGroups();
        Optional<GroupNameAndArn> thingGroupOptional = thingGroupIterable.stream().findFirst();
        emptyDeployment(thingGroupOptional.get().groupArn());
    }

    @SuppressWarnings("MissingJavadocMethod")
    public void emptyDeployment(String targetArn) {
        GetDeploymentResponse response;
        CreateDeploymentResponse created;
        GreengrassV2Client ggv2client = GreengrassV2Client.create();

        long start = System.currentTimeMillis();
        long end = start + 60 * 1000;

        Map<String, ComponentDeploymentSpecification> emptyComponent =
                new HashMap<>();

        created = ggv2client.createDeployment(CreateDeploymentRequest.builder()
                .targetArn(targetArn)
                .components(emptyComponent)
                .build());

        LOGGER.info("Waiting for Empty deployment to finish");
        while (System.currentTimeMillis() < end) {
            response = ggv2client.getDeployment(GetDeploymentRequest.builder()
                    .deploymentId(created.deploymentId())
                    .build());
            if (response.deploymentStatus().toString().equals("COMPLETED")) {
                LOGGER.info("Empty deployment reached COMPLETED state");
                break;
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
