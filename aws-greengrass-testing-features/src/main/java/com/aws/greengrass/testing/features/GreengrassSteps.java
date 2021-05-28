package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import javax.inject.Inject;
import java.io.IOException;

@ScenarioScoped
public class GreengrassSteps {
    private final Greengrass greengrass;
    private final GreengrassContext greengrassContext;
    private final TestContext testContext;
    private final Device device;

    @Inject
    public GreengrassSteps(
            final Device device,
            final Greengrass greengrass,
            final GreengrassContext greengrassContext,
            final TestContext testContext) {
        this.device = device;
        this.greengrass = greengrass;
        this.greengrassContext = greengrassContext;
        this.testContext = testContext;
    }

    public void install() throws IOException {
        device.copy(greengrassContext.greengrassPath(), testContext.testDirectory().resolve("greengrass"));
    }

    @Given("my device is running Greengrass")
    @When("I start Greengrass")
    public void start() throws IOException {
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
}
