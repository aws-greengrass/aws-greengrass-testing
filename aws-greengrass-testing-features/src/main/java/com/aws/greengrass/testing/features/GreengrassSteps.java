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

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class GreengrassSteps implements Closeable {
    private final Greengrass greengrass;
    private final GreengrassContext greengrassContext;
    private final TestContext testContext;
    private final Device device;
    private final FileSteps files;

    @Inject
    public GreengrassSteps(
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

    @When("I install Greengrass")
    public void install() {
        device.copyTo(greengrassContext.greengrassPath(), testContext.testDirectory().resolve("greengrass"));
        greengrass.install();
        files.checkFileExists("logs/greengrass.log");
    }

    @Given("my device is running Greengrass")
    @When("I start Greengrass")
    public void start() throws IOException, InterruptedException {
        install();
        greengrass.start();
        launchedSuccessfully();
    }

    @Then("Greengrass core is running on my device")
    public void launchedSuccessfully() throws InterruptedException {
        files.contains("output.log", "successfully");
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
        //stop();
    }
}
