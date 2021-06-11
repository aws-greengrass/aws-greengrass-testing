package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.TestRuns;
import com.aws.greengrass.testing.api.model.TestRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScenarioTestRuns implements TestRuns {
    private final List<TestRun> scenarios;

    public ScenarioTestRuns() {
        scenarios = new ArrayList<>();
    }

    @Override
    public List<TestRun> tracking() {
        return Collections.unmodifiableList(scenarios);
    }

    @Override
    synchronized public void track(TestRun run) {
        scenarios.add(run);
    }
}
