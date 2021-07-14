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
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StepTrackingReporting implements EventListener {
    private final Map<UUID, Logger> scenarioToLogger = new ConcurrentHashMap<>();
    private final Map<UUID, TestRun.Builder> inflightRuns = new ConcurrentHashMap<>();
    private final TestRuns testRuns = ScenarioTestRuns.instance();

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestCaseStarted.class, this::handleScenarioStarted);
        eventPublisher.registerHandlerFor(TestStepStarted.class, this::handleStepStarted);
        eventPublisher.registerHandlerFor(TestStepFinished.class, this::handleStepFinished);
        eventPublisher.registerHandlerFor(TestCaseFinished.class, this::handleScenarioFinished);
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
            if (stepFinished.getResult().getStatus() == Status.FAILED) {
                logger.error("Failed step: '{}'",
                        step.getStep().getText(),
                        stepFinished.getResult().getError());
                inflightRuns.computeIfPresent(stepFinished.getTestCase().getId(),
                        (key, run) -> run.failed(true).message("Failed '" + step.getStep().getText() + "'"));
            } else {
                logger.debug("Finished step: '{}' with status {}",
                        step.getStep().getText(),
                        stepFinished.getResult().getStatus());
            }
        }
    }

    private void handleScenarioFinished(final TestCaseFinished scenarioFinished) {
        Logger logger = scenarioToLogger.remove(scenarioFinished.getTestCase().getId());
        inflightRuns.computeIfPresent(scenarioFinished.getTestCase().getId(),
                (key, run) -> run.duration(scenarioFinished.getResult().getDuration())
                        .skipped(scenarioFinished.getResult().getStatus().equals(Status.SKIPPED))
                        .passed(scenarioFinished.getResult().getStatus().equals(Status.PASSED)));
        if (Objects.nonNull(logger)) {
            logger.debug("Finished '{}'", scenarioFinished.getTestCase().getName());
        }
    }
}
