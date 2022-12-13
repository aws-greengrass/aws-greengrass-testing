/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.io.IOException;
import javax.inject.Inject;

/**
 * Checks the connectivity for platfroms.
 *
 * @throws IOException {throws IOException}
 * @throws InterruptedException {throws IInterruptedException}
 */
@ScenarioScoped
public class CommonConnectivitySteps {
    private final Platform platform;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public CommonConnectivitySteps(Platform platform) {
          this.platform = platform;
    }

    /**
     * Checks the connectivity for platfroms.
     *
     * @param connectivity checks platfrom connectivity
     * @throws IOException          {throws IOException}
     * @throws InterruptedException {throws IInterruptedException}
     */
    @Given("device network connectivity is {word}")
    @When("I set device network connectivity to {word}")
    public void setDeviceNetwork(final String connectivity) throws IOException, InterruptedException {
        if ("offline".equalsIgnoreCase(connectivity)) {
            platform.getNetworkUtils().disconnectNetwork();
        } else {
            platform.getNetworkUtils().recoverNetwork();
        }
    }

    @After
    public void afterEachScenario()throws IOException, InterruptedException {
        platform.getNetworkUtils().recoverNetwork();
    }

}

