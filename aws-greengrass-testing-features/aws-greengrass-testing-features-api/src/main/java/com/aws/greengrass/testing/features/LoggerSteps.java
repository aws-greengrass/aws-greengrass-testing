/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.model.TestContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

@ScenarioScoped
public class LoggerSteps {
    private static final Pattern FEATURE = Pattern.compile("/(\\w+)\\.feature");
    private static final Logger LOGGER = LogManager.getLogger(LoggerSteps.class);
    private static final String OTF_VERSION_PREFIX = "otf";
    private final TestContext testContext;

    @Inject
    public LoggerSteps(final TestContext testContext) {
        this.testContext = testContext;
    }

    /**
     * Setup the logging context for the thread local context.
     *
     * @param scenario unique {@link Scenario}
     */
    @Before
    public void addContext(final Scenario scenario) {
        final Matcher matcher = FEATURE.matcher(scenario.getId());
        // If we can't get the expected file, at least we demonstrate where we are reading from.
        String feature = scenario.getUri().getPath();
        if (matcher.find()) {
            feature = matcher.group(1);
        }

        // get otf version and bake it into log
        final String otfVersionContent = getOTFVersionLogContent();
        ThreadContext.put("otfversion", otfVersionContent);
        LOGGER.info("OTF Version is {}", otfVersionContent);

        ThreadContext.put("testId", testContext.testId().prefixedId());
        ThreadContext.put("feature", feature);
        ThreadContext.put("scenarioId", scenario.getName());
        LOGGER.info("Attaching thread context to scenario: '{}'", scenario.getName());

    }

    @After(order = 1)
    public void clearContext(final Scenario scenario) {
        LOGGER.info("Clearing thread context on scenario: '{}'", scenario.getName());
        ThreadContext.clearMap();
    }

    private String getOTFVersionLogContent() {
        final String otfVersion = LoggerSteps.class.getPackage().getImplementationVersion();
        return String.format("%s-%s", OTF_VERSION_PREFIX, otfVersion);
    }
}
