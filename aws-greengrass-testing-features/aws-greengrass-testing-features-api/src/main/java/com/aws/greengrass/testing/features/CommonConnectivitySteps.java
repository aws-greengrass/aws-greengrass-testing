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
    private boolean offline;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public CommonConnectivitySteps(Platform platform) {
          this.platform = platform;
    }

    /**
     * Checks the connectivity for platforms.
     *
     * @param connectivity checks platform connectivity
     * @throws IOException          {throws IOException}
     * @throws InterruptedException {throws IInterruptedException}
     */

    @When("device network connectivity is {word}")
    public void setDeviceNetwork(final String connectivity) throws IOException, InterruptedException {
        if ("offline".equalsIgnoreCase(connectivity)) {
            platform.getNetworkUtils().disconnectNetwork();
            offline=true;
        } else {
            platform.getNetworkUtils().recoverNetwork();
        }
    }
    @After
    public void teardown() throws IOException, InterruptedException {
        if (offline) {
            platform.getNetworkUtils().recoverNetwork();
            offline=false;
        }
    }
}

