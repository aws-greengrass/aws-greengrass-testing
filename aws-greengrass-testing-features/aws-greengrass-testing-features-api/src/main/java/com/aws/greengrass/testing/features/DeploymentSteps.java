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
import com.google.common.annotations.VisibleForTesting;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.greengrassv2.model.ComponentConfigurationUpdate;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;
import software.amazon.awssdk.services.greengrassv2.model.EffectiveDeploymentExecutionStatus;
import software.amazon.awssdk.services.iot.model.GroupNameAndArn;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private static final String IOT_JOB_EXECUTION_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final Path LOCAL_STORE_RECIPES = Paths.get("local:", "local-store", "recipes");
    private final AWSResources resources;
    private final ComponentPreparationService componentPreparation;
    private final ComponentOverrides overrides;
    private final TestContext testContext;
    private final WaitSteps waits;
    private final ObjectMapper mapper;
    private final ScenarioContext scenarioContext;

    @VisibleForTesting
    GreengrassDeploymentSpec deployment;

    private Platform platform;
    private Path artifactPath;
    private Path recipePath;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public DeploymentSteps(
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
        this.artifactPath = testContext.installRoot().resolve(LOCAL_STORE).resolve(ARTIFACTS_DIR);;
        this.recipePath = testContext.installRoot().resolve(LOCAL_STORE).resolve(RECIPE_DIR);
    }

    /**
     * Create a scenario based deployment configuration.
     *
     * @param componentNames collection of component name and version tuples
     */
    @Given("I create a Greengrass deployment with components")
    public void createDeployment(List<List<String>> componentNames) {
        IotThing thing = getIotThing();
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
     * Get IoT Thing name.
     * @return
     */
    @VisibleForTesting
    IotThing getIotThing() {
        IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
        return lifecycle.thingByThingName(testContext.coreThingName());
    }

    /**
     * Get IoT Thing groups for a Thing.
     * @return
     */
    @VisibleForTesting
    SdkIterable<GroupNameAndArn> getThingGroupIterable(String coreThingName) {
        IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
        return lifecycle.listThingGroupsForAThing(coreThingName).thingGroups();
    }

    /**
     * implemented the step of installing a custom component with configuration. This requires Recipe for the custom
     * compoenent.
     *
     * @param componentName         the name of the custom component
     * @param configurationTable    the table which describes the configurations
     * @throws InterruptedException InterruptedException could be throw out during the component deployment
     * @throws IOException          IOException could be throw out during preparation of the CLI command
     */
    @When("I install the component {word} from local store with configuration")
    public void installComponentWithConfiguration(final String componentName, final String configurationTable)
            throws InterruptedException, IOException {
        // read the recipe from local store, and get component name and version from recipe
        List<String> componentSpecs = Arrays.asList(
                componentName, LOCAL_STORE_RECIPES.resolve(String.format("%s.yaml", componentName)).toString()
        );

        // handle the config input
        List<Map<String, Object>> configuration = readConfigurationTable(configurationTable);
        createLocalDeploymentWithConfig(new ArrayList<>(Collections.singleton(componentSpecs)),
                configuration, 0);
    }

    private List<Map<String, Object>> readConfigurationTable(String configurationTable) throws JsonProcessingException {
        List<Map<String, Object>> configuration = new ArrayList<>();
        String updatedConfiguration = this.scenarioContext.applyInline(configurationTable);
        Map<String, Object> json = this.mapper.readValue(updatedConfiguration,
                new TypeReference<Map<String, Object>>() {});
        configuration.add(json);
        return configuration;
    }

    private void createLocalDeploymentWithConfig(List<List<String>> componentNames,
                                                 List<Map<String, Object>> configuration,
                                                 int retryCount) throws InterruptedException, IOException {
        // find the component artifacts and copy into a local store
        final Map<String, ComponentDeploymentSpecification> components = parseComponentNamesAndPrepare(componentNames);

        List<String> commandArgs = new ArrayList<>();

        commandArgs.addAll(Arrays.asList("deployment", "create",
                "--artifactDir "  + artifactPath.toString(),
                "--recipeDir " + recipePath.toString()));

        for (Map.Entry<String, ComponentDeploymentSpecification> entry : components.entrySet()) {
            String componentName = entry.getKey();
            commandArgs.add(String.format(" --merge %s=%s", componentName, entry.getValue().componentVersion()));
            String updateConfigArgs = getCliUpdateConfigArgs(componentName, configuration);
            if (!updateConfigArgs.isEmpty()) {
                commandArgs.add("--update-config '" + updateConfigArgs + "'");
            }
        }

        executeCommand(retryCount, commandArgs);
    }

    private String getCliUpdateConfigArgs(String componentName, List<Map<String, Object>> configuration)
            throws IOException {
        Map<String, Map<String, Object>> configurationUpdate = new HashMap<>();
        // config update for each component, in the format of <componentName, <MERGE/RESET, map>>
        for (Map<String, Object> configKeyValue : configuration) {
            configurationUpdate.put(componentName, configKeyValue);
        }
        if (configurationUpdate.isEmpty()) {
            return "";
        }
        return mapper.writeValueAsString(configurationUpdate);
    }

    /**
     * Create a local deployment using greengrass cli.
     * @param componentNames map of component name to source of the component
     * @throws InterruptedException Task interrupted
     */
    @When("I create a local deployment with components")
    public void createLocalDeployment(List<List<String>> componentNames) throws InterruptedException {
        createLocalDeployment(componentNames, 0);
    }

    private void createLocalDeployment(List<List<String>> componentNames, int retryCount) throws InterruptedException {
        // find the component artifacts and copy into a local store
        final Map<String, ComponentDeploymentSpecification> components = parseComponentNamesAndPrepare(componentNames);

        List<String> commandArgs = new ArrayList<>();

        commandArgs.addAll(Arrays.asList("deployment", "create",
                "--artifactDir "  + artifactPath.toString(),
                "--recipeDir " + recipePath.toString()));

        for (Map.Entry<String, ComponentDeploymentSpecification> entry : components.entrySet()) {
            commandArgs.add(" --merge ");
            commandArgs.add(entry.getKey() + "=" + entry.getValue().componentVersion());
        }

        executeCommand(retryCount, commandArgs);
    }

    private void executeCommand(int retryCount, List<String> commandArgs)
            throws InterruptedException {
        try {
            String response = platform.commands().executeToString(CommandInput.builder()
                    .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                    .addAllArgs(commandArgs)
                    .build());
            LOGGER.debug("The response from executing gg-cli command is {}", response);
            String[] responseArray = response.split(":");
            String deploymentId = responseArray[responseArray.length - 1];
            LOGGER.info("The local deployment response is " + deploymentId);
            scenarioContext.put(LOCAL_DEPLOYMENT_ID, deploymentId);
        } catch (Exception e) {
            if (retryCount > 3) {
                throw e;
            }

            waits.until(5, "SECONDS");
            LOGGER.warn("the deployment request threw an exception, retried {} times...", retryCount);
            this.executeCommand(retryCount + 1, commandArgs);
        }
    }

    @VisibleForTesting
    Map<String, ComponentDeploymentSpecification> parseComponentNamesAndPrepare(
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

    @When("I deploy the Greengrass deployment configuration to thing group")
    public void startDeploymentForThingGroup() {
        startDeploymentForThingGroup(testContext.testId().idFor("ggc-group"));
    }

    /**
     * Deploying to a thing group with the given name.
     * @param thingGroupName name of the thing group
     */
    @When("I deploy the Greengrass deployment configuration to thing group {}")
    public void startDeploymentForThingGroup(String thingGroupName) {
        SdkIterable<GroupNameAndArn> thingGroupIterable = getThingGroupIterable(testContext.coreThingName());
        Optional<GroupNameAndArn> thingGroupOptional;
        //Get the first thing group from list and deploy to it
        if (testContext.initializationContext().persistInstalledSoftware()) {
            thingGroupOptional = thingGroupIterable.stream().findFirst();
            if (!thingGroupOptional.isPresent()) {
                throw new IllegalStateException(String.format("No thing group found for the thing name %s",
                        testContext.coreThingName()));
            }
        } else {
            thingGroupOptional = thingGroupIterable.stream()
                    .filter(g -> g.groupName().equals(thingGroupName))
                    .findFirst();
            if (!thingGroupOptional.isPresent()) {
                throw new IllegalStateException(String.format("The thing group %s not found for the thing name %s",
                        thingGroupName, testContext.coreThingName()));
            }
        }
        deployment = deployment.withThingArn(null) // setting it to null will trigger group deployment
                .withThingGroupArn(thingGroupOptional.get().groupArn());
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

    @VisibleForTesting
    Optional<EffectiveDeploymentExecutionStatus> effectivelyDeploymentStatus() {
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
                // SUCCEEDED is not a valid EffectiveDeploymentExecutionStatus enum. Possibly a bug in SDK.
                .map(d -> d.coreDeviceExecutionStatusAsString().equals(IOT_JOB_EXECUTION_STATUS_SUCCEEDED)
                        ? EffectiveDeploymentExecutionStatus.COMPLETED
                        : d.coreDeviceExecutionStatus());
    }

    /**
     * Get the target arns for thing anf thing group and
     * run an empty deployment.
     */
    @After(order = 999999)
    public void cleanupDeployments() {
        if (testContext.initializationContext().persistInstalledSoftware()) {
            try {
                IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
                IotThing thing = lifecycle.thingByThingName(testContext.coreThingName());
                emptyDeployment(thing.thingArn());

                SdkIterable<GroupNameAndArn> thingGroupIterable =
                        lifecycle.listThingGroupsForAThing(testContext.coreThingName()).thingGroups();
                Optional<GroupNameAndArn> thingGroupOptional = thingGroupIterable.stream().findFirst();
                emptyDeployment(thingGroupOptional.get().groupArn());
            } catch (InterruptedException e) {
                LOGGER.warn("Empty deployment did not reach COMPLETED");
            }
        }
    }

    /**
     * Run an empty deployment given a target arn.
     * @param targetArn target arn for the thing/thingGroup for empty deployment
     * @throws InterruptedException when the wait is interrupted
     */
    @VisibleForTesting
    void emptyDeployment(String targetArn) throws InterruptedException {
        Map<String, ComponentDeploymentSpecification> emptyComponent =
                new HashMap<>();

        GreengrassV2Lifecycle ggv2 = resources.lifecycle(GreengrassV2Lifecycle.class);

        GreengrassDeploymentSpec deployment = GreengrassDeploymentSpec.builder()
                .deploymentName("EmptyDeployment")
                .components(emptyComponent)
                .thingArn(targetArn)
                .build();

        LOGGER.info("Cleaning up component through an empty deployment");
        deployment = resources.create(deployment);
        String deploymentId = deployment.resource().deploymentId();
        checkADeploymentReachesCompleted(ggv2, deploymentId);
    }

    /**
     * Check if a deployment reaches COMPLETED status.
     * @param ggv2 greengrass v2 client
     * @param deploymentId deployment id
     */
    @VisibleForTesting
    void checkADeploymentReachesCompleted(GreengrassV2Lifecycle ggv2, String deploymentId) {
        //TODO: use effectivelyDeploymentStatus() to check status
        try {
            if (!waits.untilTrue(() -> ggv2.deployment(deploymentId)
                            .deploymentStatus().toString().equals("COMPLETED"),
                    60, TimeUnit.SECONDS)) {
                LOGGER.warn("Empty deployment did not reach COMPLETED");
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Empty deployment was interrupted");
            Thread.currentThread().interrupt();
        }
    }
}
