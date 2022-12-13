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
     * Blocks the traffic port (IP ports) on ports 443,8888,8889 and when the connectivity parameter is "offline" and
     * re-enables traffic on the ports when it is "online".
     *
     * @param connectivity desired connectivity status ("offline", "online")
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
                throw new UnsupportedOperationException("Connectivity " + connectivity + " is not supported");
        }

        offline = connectivity.equalsIgnoreCase("offline");
    }

    @After
    private void teardown() throws IOException, InterruptedException {
        if (offline) {
            platform.getNetworkUtils().recoverNetwork();
        }
    }
}

