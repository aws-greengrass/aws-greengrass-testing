/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

@ScenarioScoped
public class NetworkUtilsSteps {
    private static final Logger LOGGER = LogManager.getLogger(NetworkUtilsSteps.class);

    private final Platform platform;
    private final AtomicBoolean mqttConnectivity = new AtomicBoolean(true);

    @Inject
    public NetworkUtilsSteps(final Platform platform) {
        super();
        this.platform = platform;
    }

    /**
     * Convert offline/online string to boolean.
     *
     * @param status the string of offline/online status
     * @throws RuntimeException on invalid value
     */
    @SuppressWarnings("PMD.UnnecessaryAnnotationValueElement")
    @ParameterType(value = "offline|Offline|OFFLINE|online|Online|ONLINE")
    public boolean connectivityValue(String status) {
        switch (status) {
            case "offline":
            case "Offline":
            case "OFFLINE":
                return false;

            case "online":
            case "Online":
            case "ONLINE":
                return true;

            default:
                LOGGER.error("Invalid connectivity status {}", status);
                throw new RuntimeException("Invalid connectivity status " + status);
        }
    }

    /**
     * Disables or dnables device MQTT connectivity to IoT Core by blocking traffic on ports 8883 and 443.
     *
     * @param connectivity the value of connectivity to set
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @When("I set device mqtt connectivity to {connectivityValue}")
    public void setDeviceMqtt(final boolean connectivity) throws IOException, InterruptedException {
        boolean changed = mqttConnectivity.compareAndSet(!connectivity, connectivity);
        if (changed) {
            if (connectivity) {
                LOGGER.info("Unblocking MQTT connections");
                platform.networkUtils().recoverMqtt();
            } else {
                LOGGER.info("Blocking MQTT connections");
                platform.networkUtils().disconnectMqtt();
            }
        }
    }

    /**
     * Add IP address to loopback interface.
     *
     * @param address the value of loop back address
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @Then("I add IP address {string} to loopback interface")
    public void addLoopBackAddress(final String address) throws IOException, InterruptedException {
        LOGGER.info("Adding loopback address {}", address);
        platform.networkUtils().addLoopbackAddress(address);
    }

    /**
     * Remove IP address from loopback interface.
     *
     * @param address the value of loop back address
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @Then("I remove IP address {string} from loopback interface")
    public void removeLoopBackAddress(final String address) throws IOException, InterruptedException {
        LOGGER.info("Deleting loopback address {}", address);
        platform.networkUtils().deleteLoopbackAddress(address);
    }

    /**
     * Restore settings to defaults.
     *
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @After(order = Integer.MAX_VALUE)
    public void restoreDefaultSettings() throws IOException, InterruptedException {
        boolean changed = mqttConnectivity.compareAndSet(false, true);
        if (changed) {
            LOGGER.info("Automatically unblocking blocked MQTT connections");
            platform.networkUtils().recoverMqtt();
        }
    }
}
