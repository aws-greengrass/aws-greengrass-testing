/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.google.common.annotations.VisibleForTesting;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@ScenarioScoped
public class GreengrassCliSteps {

    public static final String LOCAL_DEPLOYMENT_ID = "localDeploymentId";

    private final Platform platform;
    private final TestContext testContext;
    private final ScenarioContext scenarioContext;
    private final ComponentPreparationService componentPreparation;
    private final WaitSteps waitSteps;

    private static final Logger LOGGER = LogManager.getLogger(GreengrassCliSteps.class);

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public GreengrassCliSteps(Platform platform, TestContext testContext,
                       ComponentPreparationService componentPreparation,
                       ScenarioContext scenarioContext, WaitSteps waitSteps) {
        this.platform = platform;
        this.testContext = testContext;
        this.componentPreparation = componentPreparation;
        this.scenarioContext = scenarioContext;
        this.waitSteps = waitSteps;
    }

    /**
     * Verifies the cli binary is installed at GG root.
     */
    @And("I verify greengrass-cli is available in greengrass root")
    public void verifyCliInstallation() {
        Path cliPath = testContext.installRoot().resolve("bin").resolve("greengrass-cli");
        if (!platform.files().exists(cliPath)) {
            throw new IllegalStateException("The cli binary is not present at the GG root");
        }
    }

    /**
     * Verify a component status using the greengrass-cli.
     *
     * @param componentName name of the component
     * @param status        {RUNNING, BROKEN, FINISHED}
     * @throws InterruptedException {@link InterruptedException}
     * @throws IllegalStateException {@link IllegalStateException}
     */
    @And("I verify the {word} component is {word} using the greengrass-cli")
    public void verifyComponentIsRunning(String componentName, String status) throws InterruptedException {
        if (!waitSteps.untilTrue(() -> this.isComponentInState(componentName, status),
                60,
                TimeUnit.SECONDS)) {
            throw new IllegalStateException("Component status is not in " + status + " status");
        }
    }

    /**
     * Verify status of local deployment.
     * @param status desired status
     * @param value integer value duration
     * @param unit {@link TimeUnit} for the duration
     * @throws InterruptedException {@link InterruptedException}
     */
    @And("the local Greengrass deployment is {word} on the device after {int} {word}")
    public void verifyLocalDeployment(String status, int value, String unit) throws InterruptedException {
        List<String> terminalStatuses = new ArrayList<>();
        terminalStatuses.add("SUCCEEDED");
        terminalStatuses.add("FAILED");
        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        waitSteps.untilTerminal(this::getLocalDeploymentStatus, status::equals,
                terminalStatuses::contains, value, timeUnit);
    }

    /**
     * Use greengrass-cli to start or stop the component.
     *
     * @param componentName Name of the component
     * @param action Action to take: stop or restart
     * @throws UnsupportedOperationException if action word is other than stop or restart
     */
    @When("I use greengrass-cli to {word} the component {word}")
    public void restartOrStopComponent(String action, String componentName) {
        if (action.matches("stop|restart")) {
            platform.commands().executeToString(CommandInput.builder()
                    .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                    .addAllArgs(Arrays.asList("component", action, "--names", componentName))
                    .build());
            LOGGER.debug("Performing {} on component {}", action, componentName);
        } else {
            throw new UnsupportedOperationException("Invalid action: "
                    + action + ". Please use restart or stop action words.");
        }

    }

    @VisibleForTesting
    String getLocalDeploymentStatus() {
        try {
            String deploymentId = scenarioContext.get(LOCAL_DEPLOYMENT_ID);
            String response = platform.commands().executeToString(CommandInput.builder()
                    .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                    .addAllArgs(Arrays.asList("deployment", "status", "--deploymentId", deploymentId))
                    .build());
            LOGGER.debug(String.format("deployment status response received for deployment ID %s is %s",
                    deploymentId, response));
            String[] responseArray = response.split(":");
            return responseArray[responseArray.length - 1].trim();
        } catch (CommandExecutionException e) {
            LOGGER.info("Exception occurred while getting the deployment status. Will try again", e);
        }
        return "";
    }

    private boolean isComponentInState(String componentName, String componentStatus) {
        String response = platform.commands().executeToString(CommandInput.builder()
                .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                .addAllArgs(Arrays.asList("component", "details", "--name", componentName))
                .build());
        LOGGER.debug(String.format("component status response received for component %s is %s",
                componentName, response));

        return response.contains(String.format("State: %s", componentStatus));
    }
}
