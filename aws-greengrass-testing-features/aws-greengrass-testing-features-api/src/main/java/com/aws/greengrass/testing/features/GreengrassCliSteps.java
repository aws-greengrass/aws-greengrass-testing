/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static com.aws.greengrass.testing.component.LocalComponentPreparationService.ARTIFACTS_DIR;
import static com.aws.greengrass.testing.component.LocalComponentPreparationService.LOCAL_STORE;
import static com.aws.greengrass.testing.component.LocalComponentPreparationService.RECIPE_DIR;

@ScenarioScoped
public class GreengrassCliSteps {

    public static final String LOCAL_DEPLOYMENT_ID = "localDeploymentId";

    private Platform platform;
    private Path artifactPath;
    private Path recipePath;
    private TestContext testContext;
    private ScenarioContext scenarioContext;
    private ComponentPreparationService componentPreparation;
    private WaitSteps waitSteps;

    private static Logger LOGGER = LogManager.getLogger(GreengrassCliSteps.class);

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
        this.artifactPath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(ARTIFACTS_DIR);;
        this.recipePath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(RECIPE_DIR);
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
     */
    @And("I verify the {word} component is {word} using the greengrass-cli")
    public void verifyComponentIsRunning(String componentName, String status) throws InterruptedException {
        waitSteps.untilTrue(() -> this.getComponentStatus(componentName, status), 30, TimeUnit.SECONDS);
    }

    /**
     * Verify status of local deployment.
     * @param status desired status
     * @param timeout timeout in seconds
     * @throws InterruptedException {@link InterruptedException}
     */
    @And("the local Greengrass deployment is {word} on the device after {int} seconds")
    public void verifyLocalDeployment(String status, int timeout) throws InterruptedException {
        List<String> terminalStatuses = new ArrayList<>();
        terminalStatuses.add("SUCCEEDED");
        terminalStatuses.add("FAILED");
        waitSteps.untilTerminal(() -> this.getLocalDeploymentStatus(), status::equals,
                terminalStatuses::contains, timeout, TimeUnit.SECONDS);
    }

    private String getLocalDeploymentStatus() {
        String deploymentId = scenarioContext.get(LOCAL_DEPLOYMENT_ID);
        String response = platform.commands().executeToString(CommandInput.builder()
                .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                .addAllArgs(Arrays.asList("deployment", "status", "--deploymentId", deploymentId))
                .build());
        LOGGER.debug(String.format("deployment status response received for deployment ID %s is %s",
                deploymentId, response));

        String[] responseArray = response.split(":");
        return responseArray[responseArray.length - 1].trim();
    }

    private boolean getComponentStatus(String componentName, String componentStatus) {
        String response = platform.commands().executeToString(CommandInput.builder()
                .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                .addAllArgs(Arrays.asList("component", "details", "--name", componentName))
                .build());
        LOGGER.debug(String.format("component status response received for component %s is %s",
                componentName, response));

        return response.contains(String.format("State: %s", componentStatus));
    }
}
