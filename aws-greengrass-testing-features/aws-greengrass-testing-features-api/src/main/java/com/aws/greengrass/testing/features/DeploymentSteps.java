/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeploymentSpec;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.greengrassv2.model.ComponentConfigurationUpdate;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;
import software.amazon.awssdk.services.greengrassv2.model.EffectiveDeployment;
import software.amazon.awssdk.services.greengrassv2.model.EffectiveDeploymentExecutionStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class DeploymentSteps {
    private static final Logger LOGGER = LogManager.getLogger(DeploymentSteps.class);
    private final AWSResources resources;
    private final ComponentPreparationService componentPreparation;
    private final ComponentOverrides overrides;
    private final TestContext testContext;
    private final GreengrassContext greengrassContext;
    private final WaitSteps waits;
    private final ObjectMapper mapper;
    private final ScenarioContext scenarioContext;
    private GreengrassDeploymentSpec deployment;

    @Inject
    DeploymentSteps(
            final AWSResources resources,
            final ComponentOverrides overrides,
            final TestContext testContext,
            final GreengrassContext greengrassContext,
            final ComponentPreparationService componentPreparation,
            final ScenarioContext scenarioContext,
            final WaitSteps waits,
            final ObjectMapper mapper) {
        this.resources = resources;
        this.overrides = overrides;
        this.testContext = testContext;
        this.greengrassContext = greengrassContext;
        this.componentPreparation = componentPreparation;
        this.scenarioContext = scenarioContext;
        this.waits = waits;
        this.mapper = mapper;
    }

    /**
     * Create a scenario based deployment configuration.
     *
     * @param componentNames collection of component name and version tuples
     */
    @Given("I create a Greengrass deployment with components")
    public void createDeployment(List<List<String>> componentNames) {
        IotThingSpec thingSpec = resources.trackingSpecs(IotThingSpec.class)
                .filter(thing -> thing.resource().thingName().equals(testContext.testId().idFor("ggc-thing")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A Greengrass deployment needs a valid target."));
        final Map<String, ComponentDeploymentSpecification> components =
                new HashMap<String, ComponentDeploymentSpecification>() {{
                    put("aws.greengrass.Nucleus", ComponentDeploymentSpecification.builder()
                            .componentVersion(greengrassContext.version())
                            .build());
                }};
        LOGGER.debug("Potential overrides: {}", overrides);
        componentNames.forEach(tuple -> {
            String name = tuple.get(0);
            String value = tuple.get(1);
            ComponentOverrideNameVersion.Builder overrideNameVersion = ComponentOverrideNameVersion.builder()
                    .name(name);
            String[] parts = value.split(":", 2);
            if (parts.length == 2) {
                overrideNameVersion.version(ComponentOverrideVersion.of(parts[0], parts[1]));
            } else {
                overrideNameVersion.version(ComponentOverrideVersion.of("cloud", parts[0]));
            }
            overrides.component(name).ifPresent(overrideNameVersion::from);
            ComponentDeploymentSpecification.Builder builder = ComponentDeploymentSpecification.builder();
            componentPreparation.prepare(overrideNameVersion.build()).ifPresent(nameVersion -> {
                builder.componentVersion(nameVersion.version().value());
            });
            components.put(name, builder.build());
        });
        LOGGER.debug("Creating deployment configuration with components to {}: {}",
                thingSpec.thingName(), components);
        deployment = GreengrassDeploymentSpec.builder()
                .deploymentName(testContext.testId().idFor("gg-deployment"))
                .thingArn(thingSpec.resource().thingArn())
                .putAllComponents(components)
                .build();
    }

    /**
     * Update a deployment configuration unique to this scenario.
     *
     * @param componentName unique string component name
     * @param configurationUpdate full configuration for component and content replaced by {@link ScenarioContext}
     * @throws JsonProcessingException failure to process configuration value
     */
    @SuppressWarnings("unchecked")
    @When("I update my Greengrass deployment configuration, setting the component {word} configuration to:")
    public void updateDeployment(String componentName, String configurationUpdate) throws JsonProcessingException {
        LOGGER.debug("Replaced content: {}", scenarioContext.applyInline(configurationUpdate));
        Map<String, Object> json = mapper.readValue(scenarioContext.applyInline(configurationUpdate),
                new TypeReference<Map<String, Object>>() {});
        deployment = GreengrassDeploymentSpec.builder()
                .from(deployment)
                .putComponents(componentName, ComponentDeploymentSpecification.builder()
                        .componentVersion(deployment.components().get(componentName).componentVersion())
                        .configurationUpdate(ComponentConfigurationUpdate.builder()
                                .merge(mapper.writeValueAsString(json.get("MERGE")))
                                .reset((List<String>) json.get("RESET"))
                                .build())
                        .build())
                .build();
    }

    @When("I deploy the Greengrass deployment configuration")
    public void startDeployment() {
        deployment = resources.create(deployment);
        LOGGER.info("Created Greengrass deployment: {}", deployment.resource().deploymentId());
    }

    /**
     * Checks the current deployment is in a terminal status after a time.
     *
     * @param status Terminal {@link EffectiveDeploymentExecutionStatus}
     * @param value integer value duration
     * @param unit {@link TimeUnit} for the duration
     * @throws InterruptedException thread interrupted while waiting
     */
    @Then("the Greengrass deployment is {word} on the device after {int} {word}")
    public void deploymentSucceeds(String status, int value, String unit) throws InterruptedException {
        Set<EffectiveDeploymentExecutionStatus> terminalStatuses = new HashSet<>();
        terminalStatuses.add(EffectiveDeploymentExecutionStatus.COMPLETED);
        terminalStatuses.add(EffectiveDeploymentExecutionStatus.CANCELED);
        terminalStatuses.add(EffectiveDeploymentExecutionStatus.FAILED);
        terminalStatuses.add(EffectiveDeploymentExecutionStatus.REJECTED);
        terminalStatuses.add(EffectiveDeploymentExecutionStatus.TIMED_OUT);

        final EffectiveDeploymentExecutionStatus effectiveStatus = EffectiveDeploymentExecutionStatus.valueOf(status);
        assertTrue(terminalStatuses.contains(effectiveStatus),
                "Please target a terminal status: " + terminalStatuses);

        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        assertTrue(waits.untilTerminal(
                () -> this.effectivelyDeploymentStatus().orElse(null),
                effectiveStatus::equals,
                terminalStatuses::contains, value, timeUnit),
                "Deployment " + testContext.testId().idFor("gg-deployment") + " did not reach " + status);
    }

    private Optional<EffectiveDeploymentExecutionStatus> effectivelyDeploymentStatus() {
        GreengrassV2Lifecycle ggv2 = resources.lifecycle(GreengrassV2Lifecycle.class);
        String deploymentName = testContext.testId().idFor("gg-deployment");
        GreengrassDeploymentSpec ggcDeployment = ggv2.trackingSpecs(GreengrassDeploymentSpec.class)
                .filter(deployment -> deployment.deploymentName().equals(deploymentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find a deployment " + deploymentName));
        return ggv2.listDeviceDeployments(testContext.testId().idFor("ggc-thing")).effectiveDeployments().stream()
                .filter(deployment -> deployment.deploymentId().equals(ggcDeployment.resource().deploymentId()))
                .peek(deployment -> LOGGER.debug("Greengrass Deployment {} is in {}",
                        deployment.deploymentId(), deployment.coreDeviceExecutionStatusAsString()))
                .findFirst()
                .map(EffectiveDeployment::coreDeviceExecutionStatus);
    }
}
