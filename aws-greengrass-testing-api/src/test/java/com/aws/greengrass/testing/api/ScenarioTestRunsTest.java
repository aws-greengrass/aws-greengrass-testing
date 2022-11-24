/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;
import com.aws.greengrass.testing.api.model.TestRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScenarioTestRunsTest {
    @Test
    void GIVEN_testRunsInstanceIsCreated_WHEN_trackingTestRun_THEN_testRunsAreTracked() {
        TestRuns testRuns = ScenarioTestRuns.instance();
        assertEquals(0, testRuns.tracking().size());
        TestRun testRun = TestRun.builder().name("test_name").build();
        testRuns.track(testRun);
        assertEquals(1, testRuns.tracking().size());
    }
}
