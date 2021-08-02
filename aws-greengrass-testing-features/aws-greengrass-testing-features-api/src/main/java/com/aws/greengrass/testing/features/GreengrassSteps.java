/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.io.Closeable;
import javax.inject.Inject;

@ScenarioScoped
public class GreengrassSteps implements Closeable {
    private final Greengrass greengrass;
    private final FileSteps files;

    @Inject
    GreengrassSteps(
            final Greengrass greengrass,
            final FileSteps files) {
        this.greengrass = greengrass;
        this.files = files;
    }

    /**
     * Installs {@link Greengrass} for testing.
     */
    @When("I install Greengrass")
    public void install() {
        greengrass.install();
        files.checkFileExists("logs/greengrass.log");
    }

    /**
     * Starts a {@link Greengrass} software instance.
     *
     */
    @Given("my device is running Greengrass")
    @When("I start Greengrass")
    public void start() {
        install();
        greengrass.start();
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
    public void close() {
        stop();
    }
}
