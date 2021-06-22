package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
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
    private final Device device;
    private final FileSteps files;

    @Inject
    GreengrassSteps(
            final Device device,
            final Greengrass greengrass,
            final GreengrassContext greengrassContext,
            final TestContext testContext,
            final FileSteps files) {
        this.device = device;
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
        device.copyTo(greengrassContext.greengrassPath(), testContext.installRoot().resolve("greengrass"));
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
