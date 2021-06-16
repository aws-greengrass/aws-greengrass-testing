package com.aws.greengrass.testing.launcher.reporting;

import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
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

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestStepStarted.class, this::handleStepStarted);
        eventPublisher.registerHandlerFor(TestStepFinished.class, this::handleStepFinished);
        eventPublisher.registerHandlerFor(TestCaseFinished.class, this::handleScenarioFinished);
    }

    private void handleStepStarted(final TestStepStarted stepStarted) {
        if (stepStarted.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep step = (PickleStepTestStep) stepStarted.getTestStep();
            String path = stepStarted.getTestCase().getUri().toString().replace("classpath:", "");
            Logger logger = scenarioToLogger.computeIfAbsent(stepStarted.getTestCase().getId(),
                    key -> LogManager.getLogger(path));
            logger.info("Step on line {}: '{}'", step.getStep().getLine(), step.getStep().getText());
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
            } else {
                logger.debug("Finished step: '{}' with status {}",
                        step.getStep().getText(),
                        stepFinished.getResult().getStatus());
            }
        }
    }

    private void handleScenarioFinished(final TestCaseFinished scenarioFinished) {
        Logger logger = scenarioToLogger.remove(scenarioFinished.getTestCase().getId());
        if (Objects.nonNull(logger)) {
            logger.debug("Finished '{}'", scenarioFinished.getTestCase().getName());
        }
    }
}
