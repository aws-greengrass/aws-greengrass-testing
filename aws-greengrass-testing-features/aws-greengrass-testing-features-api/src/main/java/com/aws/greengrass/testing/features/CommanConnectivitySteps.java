/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.platform.PlatformResolver;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.io.IOException;
import javax.inject.Inject;

/**
 * Checks the connectivity for platfroms.
 *
 * @throws IOException {throws IOException}
 * @throws InterruptedException {throws IInterruptedException}
 *
 */
@ScenarioScoped
public class CommanConnectivitySteps {
    private final PlatformResolver platformResolver;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public CommanConnectivitySteps(PlatformResolver platformResolver) {
        this.platformResolver = platformResolver;
    }

    /**
     * Checks the connectivity for platfroms.
     *
     * @throws IOException {throws IOException}
     * @throws InterruptedException {throws IInterruptedException}
     * @param connectivity checks platfrom connectivity
     *
     */
    @Given("device network connectivity is {word}")
    @When("I set device network connectivity to {word}")
    public void setDeviceNetwork(final String connectivity) throws IOException, InterruptedException {
        if ("offline".equalsIgnoreCase(connectivity)) {
            platformResolver.resolve().getNetworkUtils().disconnectNetwork();
        } else {
            platformResolver.resolve().getNetworkUtils().recoverNetwork();
        }
    }
}

