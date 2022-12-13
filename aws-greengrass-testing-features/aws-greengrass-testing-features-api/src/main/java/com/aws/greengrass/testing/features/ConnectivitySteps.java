/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.When;

import java.io.IOException;
import javax.inject.Inject;

/**
 * Blocks the traffic port (IP ports) on ports 443,8888,8889 and back online it.
 * Re-enables them to simulate Network Connectivity.
 */
@ScenarioScoped
public class ConnectivitySteps {
    private final Platform platform;
    private boolean offline = false;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public ConnectivitySteps(Platform platform) {
          this.platform = platform;
    }

    /**
     * Blocks the traffic port (IP ports) on ports 443,8888,8889 and back online it.
     * Re-enables them to simulate Network Connectivity.
     *
     * @param connectivity checks platform connectivity
     * @throws IOException          {throws IOException}
     * @throws InterruptedException {throws IInterruptedException}
     * @throws UnsupportedOperationException {throws UnsupportedOperationException}
     */
    @When("the device network connectivity is {word}")
    public void setDeviceNetwork(final String connectivity) throws IOException, InterruptedException {
        switch (connectivity) {
            case "offline":
                platform.getNetworkUtils().disconnectNetwork();
                break;
            case "online":
                platform.getNetworkUtils().recoverNetwork();
                break;
            default:
                throw new UnsupportedOperationException("Connectivity " + connectivity + " is not supported ");
        }

        offline = connectivity.equalsIgnoreCase("offline");
    }

    /**
     * After Each scenario if there is any failure it will make the device go online.
     * Re-enables them to simulate Network Connectivity.
     * @throws IOException {IOException}
     * @throws InterruptedException {InterruptedException}
     */
    @After
    public void teardown() throws IOException, InterruptedException {
        if (offline) {
            platform.getNetworkUtils().recoverNetwork();
        }
    }
}

