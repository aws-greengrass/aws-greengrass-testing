package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Nucleus;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import javax.inject.Inject;

@ScenarioScoped
public class NucleusSteps {
    private final Nucleus nucleus;

    @Inject
    public NucleusSteps(final Nucleus nucleus) {
        this.nucleus = nucleus;
    }

    @Given("my device is running the Nucleus")
    @When("I start the Nucleus")
    public void start() {
        nucleus.start();
    }

    @When("I stop the Nucleus")
    public void stop() {
        nucleus.stop();
    }

    @When("I restart the Nucleus")
    public void restart() {
        nucleus.stop();
        nucleus.start();
    }
}
