package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.TestRuns;
import com.aws.greengrass.testing.api.model.TestRun;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

import java.time.Duration;
import javax.inject.Inject;

@ScenarioScoped
public class ScenarioTrackerSteps {
    private final TestRuns scenarios;
    private long startTime;

    @Inject
    ScenarioTrackerSteps(final TestRuns scenarios) {
        this.scenarios = scenarios;
    }

    @Before
    public void start(Scenario scenario) {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Stop tracking a complete {@link Scenario}.
     *
     * @param scenario unique {@link Scenario}
     */
    @After
    public void stop(Scenario scenario) {
        long duration = System.currentTimeMillis() - startTime;
        TestRun.Builder builder = TestRun.builder()
                .duration(Duration.ofMillis(duration))
                .name(scenario.getName());
        switch (scenario.getStatus()) {
            case SKIPPED:
                builder.skipped(true);
                break;
            case PASSED:
                builder.passed(true);
                break;
            default:
                builder.failed(true);
        }
        scenarios.track(builder.build());
    }
}
