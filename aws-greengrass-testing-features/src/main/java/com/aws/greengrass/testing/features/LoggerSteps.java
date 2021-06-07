package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.model.TestContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import javax.inject.Inject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ScenarioScoped
public class LoggerSteps {
    private static final Pattern FEATURE = Pattern.compile("/(\\w+)\\.feature");
    private static final Logger LOGGER = LogManager.getLogger(LoggerSteps.class);
    private final TestContext testContext;

    @Inject
    public LoggerSteps(final TestContext testContext) {
        this.testContext = testContext;
    }

    @Before
    public void addContext(final Scenario scenario) {
        final Matcher matcher = FEATURE.matcher(scenario.getId());
        // If we can't get the expected file, at least we demonstrate where we are reading from.
        String feature = scenario.getUri().getPath();
        if (matcher.find()) {
            feature = matcher.group(1);
        }
        ThreadContext.put("testId", testContext.testId().id());
        ThreadContext.put("feature", feature);
        ThreadContext.put("scenarioId", scenario.getName());
        LOGGER.info("Attaching thread context to scenario: '{}'", scenario.getName());

    }

    @After(order = 1)
    public void clearContext(final Scenario scenario) {
        LOGGER.info("Clearing thread context on scenario: '{}'", scenario.getName());
        ThreadContext.clearMap();
    }
}
