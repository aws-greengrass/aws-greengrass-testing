/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.Closeable;
import java.io.IOException;
import javax.inject.Inject;

@ScenarioScoped
public class GreengrassSteps implements Closeable {
    private final Greengrass greengrass;
    private final GreengrassContext greengrassContext;
    private final TestContext testContext;
    private final Platform platform;
    private final FileSteps files;

    @Inject
    GreengrassSteps(
            final Platform platform,
            final Greengrass greengrass,
            final GreengrassContext greengrassContext,
            final TestContext testContext,
            final FileSteps files) {
        this.platform = platform;
        this.greengrass = greengrass;
        this.greengrassContext = greengrassContext;
        this.testContext = testContext;
        this.files = files;
    }

    /**
     * Installs {@link Greengrass} for testing.
     */
    @When("I install Greengrass")
    public void install() {
        platform.files().copyTo(greengrassContext.greengrassPath(), testContext.installRoot().resolve("greengrass"));
        greengrass.install();
        files.checkFileExists("logs/greengrass.log");
    }

    /**
     * Starts a {@link Greengrass} software instance.
     *
     * @throws InterruptedException Thread is interrupted while waiting for {@link Greengrass} to start
     */
    @Given("my device is running Greengrass")
    @When("I start Greengrass")
    public void start() throws InterruptedException {
        install();
        greengrass.start();
        launchedSuccessfully();
    }

    @Then("Greengrass core is running on my device")
    public void launchedSuccessfully() throws InterruptedException {
        files.contains("output.log", "Launched Nucleus successfully");
    }

    @When("I stop Greengrass")
    public void stop() {
        greengrass.stop();
    }

    @When("I restart Greengrass")
    public void restart() {
        greengrass.stop();
        greengrass.start();
    }

    @After(order = 99999)
    public void close() throws IOException {
        stop();
    }
}
