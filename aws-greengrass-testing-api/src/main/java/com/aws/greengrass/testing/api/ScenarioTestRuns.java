/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.TestRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScenarioTestRuns implements TestRuns {
    private static final TestRuns INSTANCE = new ScenarioTestRuns();
    private final List<TestRun> scenarios;

    public static TestRuns instance() {
        return INSTANCE;
    }

    private ScenarioTestRuns() {
        scenarios = new ArrayList<>();
    }

    @Override
    public List<TestRun> tracking() {
        return Collections.unmodifiableList(scenarios);
    }

    @Override
    public synchronized void track(TestRun run) {
        scenarios.add(run);
    }
}
