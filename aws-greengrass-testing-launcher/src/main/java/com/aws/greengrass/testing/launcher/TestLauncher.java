/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.launcher.reporting.StepTrackingReporting;
import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.console.options.Details;
import org.junit.platform.console.options.Theme;
import org.junit.platform.console.tasks.ConsoleTestExecutor;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.StringJoiner;

public final class TestLauncher {
    private static final String ENGINE = "cucumber";
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "greengrass/features";

    /**
     * Start the {@link TestLauncher} wrapping a JUnit platform engine.
     *
     * @param args optional additional arguments for the runner
     * @throws Exception any runtime failure before JUnit platform engine begins
     */
    public static void main(String[] args) throws Exception {
        final Path output;
        if (args.length > 0) {
            output = Paths.get(args[0]);
        } else {
            output = Paths.get("");
        }
        CommandLineOptions options = new CommandLineOptions();
        options.setTheme(Theme.UNICODE);
        options.setDetails(Details.TREE);
        options.setIncludedEngines(Arrays.asList(ENGINE));
        String tags = System.getProperty("tags");
        if (Objects.nonNull(tags)) {
            options.setIncludedTagExpressions(Arrays.asList(tags));
        }
        options.setSelectedClasspathResources(Arrays.asList(System.getProperty("feature.path", DEFAULT_FEATURES)));
        final Path resultsXml = output.toAbsolutePath().resolve("TEST-greengrass-results.xml");
        options.setConfigurationParameters(new HashMap<String, String>() {
            {
                put("cucumber.glue", System.getProperty("glue.package", DEFAULT_GLUE_PATH));
                put("cucumber.plugin", new StringJoiner(",")
                        .add(StepTrackingReporting.class.getName())
                        .add("junit:" + resultsXml.toString())
                        .toString());
            }
        });
        final ConsoleTestExecutor executor = new ConsoleTestExecutor(options);
        final TestExecutionSummary summary = executor.execute(new PrintWriter(System.out));
        if (summary.getTestsFailedCount() > 0) {
            System.err.println("Test Failure: " + summary.getTestsFailedCount());
            System.err.println("See the full error report: " + resultsXml);
            System.exit(1);
        }
    }
}
