/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher.reporting;

import com.aws.greengrass.testing.api.ScenarioTestRuns;
import com.aws.greengrass.testing.api.TestRuns;
import com.aws.greengrass.testing.api.model.TestRun;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StepTrackingReporting implements EventListener {
    private static final Logger LOGGER = LogManager.getLogger(StepTrackingReporting.class);
    private static final String CONTEXT_TEST_ID = "testId";
    private static final String CONTEXT_FEATURE = "feature";
    private final Map<UUID, Logger> scenarioToLogger = new ConcurrentHashMap<>();
    private final Map<UUID, TestRun.Builder> inflightRuns = new ConcurrentHashMap<>();
    private final TestRuns testRuns = ScenarioTestRuns.instance();

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestCaseStarted.class, this::handleScenarioStarted);
        eventPublisher.registerHandlerFor(TestStepStarted.class, this::handleStepStarted);
        eventPublisher.registerHandlerFor(TestStepFinished.class, this::handleStepFinished);
        eventPublisher.registerHandlerFor(TestCaseFinished.class, this::handleScenarioFinished);
        eventPublisher.registerHandlerFor(TestRunFinished.class, this::handleTestSuiteFinished);
    }

    private void handleScenarioStarted(final TestCaseStarted scenarioStarted) {
        inflightRuns.computeIfAbsent(scenarioStarted.getTestCase().getId(),
                key -> TestRun.builder().name(scenarioStarted.getTestCase().getName()));
    }

    private void handleStepStarted(final TestStepStarted stepStarted) {
        if (stepStarted.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep step = (PickleStepTestStep) stepStarted.getTestStep();
            String path = stepStarted.getTestCase().getUri().toString().replace("classpath:", "");
            Logger logger = scenarioToLogger.computeIfAbsent(stepStarted.getTestCase().getId(),
                    key -> LogManager.getLogger(path));
            logger.info("line {}: '{}'", step.getStep().getLine(), step.getStep().getText());
        }
    }

    private void handleStepFinished(final TestStepFinished stepFinished) {
        Logger logger = scenarioToLogger.get(stepFinished.getTestCase().getId());
        if (Objects.nonNull(logger) && stepFinished.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep step = (PickleStepTestStep) stepFinished.getTestStep();
            TestRun.Builder builder = inflightRuns.get(stepFinished.getTestCase().getId());
            Optional.ofNullable(ThreadContext.get(CONTEXT_TEST_ID)).ifPresent(builder::testId);
            Optional.ofNullable(ThreadContext.get(CONTEXT_FEATURE)).ifPresent(builder::feature);
            if (stepFinished.getResult().getStatus() == Status.FAILED) {
                logger.error("Failed step: '{}'",
                        step.getStep().getText(),
                        stepFinished.getResult().getError());
                builder.failed(true).message("Failed '" + step.getStep().getText() + "'");
            } else {
                logger.debug("Finished step: '{}' with status {}",
                        step.getStep().getText(),
                        stepFinished.getResult().getStatus());
            }
        }
    }

    private void handleScenarioFinished(final TestCaseFinished scenarioFinished) {
        Logger logger = scenarioToLogger.remove(scenarioFinished.getTestCase().getId());
        TestRun.Builder builder = inflightRuns.remove(scenarioFinished.getTestCase().getId());
        if (Objects.nonNull(builder)) {
            TestRun run = builder.duration(scenarioFinished.getResult().getDuration())
                    .failed(scenarioFinished.getResult().getStatus().equals(Status.FAILED))
                    .skipped(scenarioFinished.getResult().getStatus().equals(Status.SKIPPED))
                    .passed(scenarioFinished.getResult().getStatus().equals(Status.PASSED))
                    .build();
            if (run.failed() && run.message() == null) {
                run = TestRun.builder()
                        .from(run)
                        .message(scenarioFinished.getResult().getError().getMessage())
                        .build();
            }
            testRuns.track(run);
        }
        if (Objects.nonNull(logger)) {
            logger.debug("Finished '{}'", scenarioFinished.getTestCase().getName());
        }
    }

    private void handleTestSuiteFinished(final TestRunFinished suiteFinished) {
        if (testRuns.tracking().isEmpty()) {
            LOGGER.warn("Suite finished reporting 0 scenarios. This will result in a failure.");
        }
        for (TestRun run : testRuns.tracking()) {
            ThreadContext.put(CONTEXT_TEST_ID, run.testId());
            ThreadContext.put(CONTEXT_FEATURE, run.feature());
            if (run.failed()) {
                LOGGER.error("Failed: '{}': {}", run.name(), run.message());
            } else {
                LOGGER.info("Passed: '{}'", run.name());
            }
            ThreadContext.clearMap();
        }
    }
}
