/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher.reporting;

import com.aws.greengrass.testing.api.model.TestRun;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.StepArgument;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StepTrackingReportingTest {

    private Instant instant = Instant.now();

    private UUID uuid = UUID.randomUUID();

    private String testName = "testName";

    private TestCase testCase = new TestCase() {
        @Override
        public Integer getLine () {
            return null;
        }

        @Override
        public String getKeyword () {
            return null;
        }

        @Override
        public String getName () {
            return testName;
        }

        @Override
        public String getScenarioDesignation () {
            return null;
        }

        @Override
        public List<String> getTags () {
            return null;
        }

        @Override
        public List<TestStep> getTestSteps () {
            return null;
        }

        @Override
        public URI getUri () {
            return URI.create("greengrass/features/cloudComponent.feature");
        }

        @Override
        public UUID getId () {
            return uuid;
        }
    };

    private TestStep invalidTestStep = new TestStep() {
        @Override
        public String getCodeLocation() {
            return null;
        }
    };

    private Step step = new Step() {
        @Override
        public StepArgument getArgument() {
            return null;
        }

        @Override
        public String getKeyWord() {
            return null;
        }

        @Override
        public String getText() {
            return "testText";
        }

        @Override
        public int getLine() {
            return 0;
        }
    };

    private PickleStepTestStep pickleStepTestStep = new PickleStepTestStep() {
        @Override
        public String getPattern() {
            return null;
        }

        @Override
        public Step getStep() {
            return step;
        }

        @Override
        public List<Argument> getDefinitionArgument() {
            return null;
        }

        @Override
        public StepArgument getStepArgument() {
            return null;
        }

        @Override
        public int getStepLine() {
            return 0;
        }

        @Override
        public URI getUri() {
            return URI.create("greengrass/features/cloudComponent.feature");
        }

        @Override
        public String getStepText() {
            return null;
        }

        @Override
        public String getCodeLocation() {
            return null;
        }
    };

    private Result result= new Result(Status.PASSED, Duration.ofMinutes(1), new Throwable("test"));


    TestCaseStarted testCaseStarted = new TestCaseStarted(instant, testCase);

    TestStepStarted invalidTestStepStarted = new TestStepStarted(instant, testCase, invalidTestStep);

    TestStepStarted validTestStepStarted = new TestStepStarted(instant, testCase, pickleStepTestStep);

    TestStepFinished testStepFinished = new TestStepFinished(instant, testCase, pickleStepTestStep, result);

    TestCaseFinished testCaseFinished = new TestCaseFinished(instant, testCase, result);


    @Test
    void GIVEN_validTestCase_WHEN_invockingHandleScenarioStarted_THEN_inflightRunsIsSetWithCorrectKeyAndValue () {
        StepTrackingReporting stepTrackingReporting = new StepTrackingReporting();
        stepTrackingReporting.handleScenarioStarted(testCaseStarted);
        Map<UUID, TestRun.Builder> inflightRuns = stepTrackingReporting.inflightRuns;
        assertEquals(1, inflightRuns.size());
        TestRun testRun = inflightRuns.get(uuid).build();
        assertEquals(testName, testRun.name());
    }

    @Test
    void GIVEN_invalidTestStep_WHEN_invockingHandleStepStarted_THEN_scenarioToLoggerIsEmpty() {
        StepTrackingReporting stepTrackingReporting = new StepTrackingReporting();
        stepTrackingReporting.handleStepStarted(invalidTestStepStarted);
        assertEquals(0, stepTrackingReporting.scenarioToLogger.size());
    }

    @Test
    void GIVEN_validTestStep_WHEN_invockingHandleStepStarted_THEN_scenarioToLoggerIsSetWithCorrectKeyAndValue() {
        StepTrackingReporting stepTrackingReporting = new StepTrackingReporting();
        stepTrackingReporting.handleStepStarted(validTestStepStarted);
        assertEquals(1, stepTrackingReporting.scenarioToLogger.size());
    }

    @Test
    void GIVEN_validTestCaseFinished_WHEN_invockingHandleScenarioFinished_THEN_testRunsCapturedRunStatus () {
        StepTrackingReporting stepTrackingReporting = new StepTrackingReporting();
        stepTrackingReporting.handleScenarioStarted(testCaseStarted);
        stepTrackingReporting.handleStepStarted(validTestStepStarted);
        stepTrackingReporting.handleStepFinished(testStepFinished);
        stepTrackingReporting.handleScenarioFinished(testCaseFinished);
        assertEquals(1, stepTrackingReporting.testRuns.tracking().size());
        assertTrue(stepTrackingReporting.testRuns.tracking().get(0).passed());
        assertFalse(stepTrackingReporting.testRuns.tracking().get(0).failed());
        assertFalse(stepTrackingReporting.testRuns.tracking().get(0).skipped());
    }
}
