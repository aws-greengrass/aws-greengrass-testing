/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.launcher.reporting.StepTrackingReporting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.console.options.Details;
import org.junit.platform.console.options.Theme;
import org.junit.platform.console.tasks.ConsoleTestExecutor;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

public final class TestLauncher {
    private static final String ENGINE = "cucumber";
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "greengrass/features";
    private static final String LOG_LEVEL = "log.level";

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
            output = Paths.get(System.getProperty("test.log.path", ""));
        }
        Files.createDirectories(output);
        addFileAppender(output);
        CommandLineOptions options = new CommandLineOptions();
        options.setTheme(Theme.UNICODE);
        options.setDetails(Details.TREE);
        options.setIncludedEngines(Arrays.asList(ENGINE));
        String tags = System.getProperty("tags");
        if (Objects.nonNull(tags)) {
            options.setIncludedTagExpressions(Arrays.asList(tags));
        }
        final List<String> resources = new ArrayList<String>() {{
            add(DEFAULT_FEATURES);
        }};
        // Allow external feature files. This enables framework features to work with static features.
        Optional.ofNullable(System.getProperty("feature.path")).ifPresent(resources::add);
        options.setSelectedClasspathResources(resources);
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

    /**
     * Update the logger with a file appender so it can be reviewed outside of console output.
     *
     * @param output the output path to place the log file
     */
    public static void addFileAppender(final Path output) {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig config = context.getConfiguration().getRootLogger();
        final Layout layout = PatternLayout.newBuilder()
                .withPattern("%d{yyyy-MMM-dd HH:mm:ss,SSS} [%X{feature}] [%X{testId}] [%level] %logger{36} - %msg%n")
                .build();
        FileAppender.Builder appenderBuilder = FileAppender.newBuilder()
                .withFileName(output.resolve("greengrass-test-run.log").toString())
                .withImmediateFlush(true)
                .withBufferedIo(false)
                .withBufferSize(8192);
        appenderBuilder.setLayout(layout)
                .setName("File");
        Appender appender = appenderBuilder.build();
        appender.start();
        final Level level = Level.valueOf(System.getProperty(LOG_LEVEL, "info"));
        config.addAppender(appender, level, null);
        config.setLevel(level);
        context.updateLoggers();
    }
}
