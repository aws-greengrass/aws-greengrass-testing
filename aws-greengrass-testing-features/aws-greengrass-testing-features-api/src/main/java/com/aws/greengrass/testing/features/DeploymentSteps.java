/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeploymentSpec;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThing;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static com.aws.greengrass.testing.component.LocalComponentPreparationService.ARTIFACTS_DIR;
import static com.aws.greengrass.testing.component.LocalComponentPreparationService.LOCAL_STORE;
import static com.aws.greengrass.testing.component.LocalComponentPreparationService.RECIPE_DIR;
import static com.aws.greengrass.testing.features.GreengrassCliSteps.LOCAL_DEPLOYMENT_ID;

@ScenarioScoped
public class DeploymentSteps {
    private static final Logger LOGGER = LogManager.getLogger(DeploymentSteps.class);
    private final AWSResources resources;
    private final ComponentPreparationService componentPreparation;
    private final ComponentOverrides overrides;
    private final TestContext testContext;
    private final WaitSteps waits;
    private final ObjectMapper mapper;
    private final ScenarioContext scenarioContext;
    private GreengrassDeploymentSpec deployment;
    private Platform platform;
    private Path artifactPath;
    private Path recipePath;

    @Inject
    DeploymentSteps(
            final AWSResources resources,
            final ComponentOverrides overrides,
            final TestContext testContext,
            final ComponentPreparationService componentPreparation,
            final ScenarioContext scenarioContext,
            final WaitSteps waits,
            final ObjectMapper mapper,
            final Platform platform) {
        this.resources = resources;
        this.overrides = overrides;
        this.testContext = testContext;
        this.componentPreparation = componentPreparation;
        this.scenarioContext = scenarioContext;
        this.waits = waits;
        this.mapper = mapper;
        this.platform = platform;
        this.artifactPath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(ARTIFACTS_DIR);;
        this.recipePath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(RECIPE_DIR);
    }

    /**
     * Create a scenario based deployment configuration.
     *
     * @param componentNames collection of component name and version tuples
     */
    @Given("I create a Greengrass deployment with components")
    public void createDeployment(List<List<String>> componentNames) {
        IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
        IotThing thing = lifecycle.thingByThingName(testContext.coreThingName());
        final Map<String, ComponentDeploymentSpecification> components =
                new HashMap<String, ComponentDeploymentSpecification>() {{
                    put("aws.greengrass.Nucleus", ComponentDeploymentSpecification.builder()
                            .componentVersion(testContext.coreVersion())
                            .build());
                }};
        LOGGER.debug("Potential overrides: {}", overrides);
        components.putAll(parseComponentNamesAndPrepare(componentNames));
        LOGGER.debug("Creating deployment configuration with components to {}: {}",
                thing.thingArn(), components);
        deployment = GreengrassDeploymentSpec.builder()
                .deploymentName(testContext.testId().idFor("gg-deployment"))
                .thingArn(thing.thingArn())
                .putAllComponents(components)
                .build();
    }

    /**
     * Create a local deployment using greengrass cli.
     * @param componentNames map of component name to source of the component
     */
    @When("I create a local deployment with components")
    public void createLocalDeployment(List<List<String>> componentNames) {
        // find the component artifacts and copy into a local store
        final Map<String, ComponentDeploymentSpecification> components = parseComponentNamesAndPrepare(componentNames);

        // execute the command
        List<String> commandArgs = new ArrayList<>();

        commandArgs.addAll(Arrays.asList("deployment", "create",
                "--artifactDir "  + platform.commands().escapeSpaces(artifactPath.toString()),
                "--recipeDir " + platform.commands().escapeSpaces(recipePath.toString())));

        for (Map.Entry<String, ComponentDeploymentSpecification> entry : components.entrySet()) {
            commandArgs.add(" --merge ");
            commandArgs.add(entry.getKey() + "=" + entry.getValue().componentVersion());
        }

        String response = platform.commands().executeToString(CommandInput.builder()
                .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                .addAllArgs(commandArgs)
                .build());
        LOGGER.debug(String.format("The response from executing gg-cli command is %s", response));
        String[] responseArray = response.split(":");
        String deploymentId = responseArray[responseArray.length - 1];
        LOGGER.info("The local deployment response is " + deploymentId);
        scenarioContext.put(LOCAL_DEPLOYMENT_ID, deploymentId);
    }

    private Map<String, ComponentDeploymentSpecification> parseComponentNamesAndPrepare(
            List<List<String>> componentNames) {
        Map<String, ComponentDeploymentSpecification> components = new HashMap<>();
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
        return components;
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
        final String updatedConfiguration = scenarioContext.applyInline(configurationUpdate);
        LOGGER.debug("Replaced content: {}", updatedConfiguration);
        Map<String, Object> json = mapper.readValue(updatedConfiguration, new TypeReference<Map<String, Object>>() {});
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
     * @throws IllegalStateException when the deployment does not succeed on the DUT
     * @throws IllegalArgumentException if status is not a terminal status
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
        if (!terminalStatuses.contains(effectiveStatus)) {
            throw new IllegalArgumentException("Please target a terminal status: " + terminalStatuses);
        }

        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        if (!waits.untilTerminal(
                () -> this.effectivelyDeploymentStatus().orElse(null),
                effectiveStatus::equals,
                terminalStatuses::contains, value, timeUnit)) {
            throw new IllegalStateException("Deployment " + testContext.testId().idFor("gg-deployment")
                    + " did not reach " + status);
        }
    }

    private Optional<EffectiveDeploymentExecutionStatus> effectivelyDeploymentStatus() {
        GreengrassV2Lifecycle ggv2 = resources.lifecycle(GreengrassV2Lifecycle.class);
        String deploymentName = testContext.testId().idFor("gg-deployment");
        GreengrassDeploymentSpec ggcDeployment = ggv2.trackingSpecs(GreengrassDeploymentSpec.class)
                .filter(deployment -> deployment.deploymentName().equals(deploymentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find a deployment " + deploymentName));
        return ggv2.listDeviceDeployments(testContext.coreThingName()).effectiveDeployments().stream()
                .filter(deployment -> deployment.deploymentId().equals(ggcDeployment.resource().deploymentId()))
                .peek(deployment -> LOGGER.debug("Greengrass Deployment {} is in {}",
                        deployment.deploymentId(), deployment.coreDeviceExecutionStatusAsString()))
                .findFirst()
                .map(EffectiveDeployment::coreDeviceExecutionStatus);
    }
}
