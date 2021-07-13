/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.launcher.reporting.StepTrackingReporting;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.feature.GluePath;
import io.cucumber.core.options.RuntimeOptionsBuilder;
import io.cucumber.core.runtime.Runtime;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

public final class TestLauncher {
    private static final Logger LOGGER = LogManager.getLogger(TestLauncher.class);
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "classpath:greengrass/features";
    private static final String LOG_LEVEL = "log.level";
    private static final String FEATURE_PATH = "feature.path";
    private static final String TEST_RESULTS_XML = "test.results.xml";
    private static final String TEST_RESULTS_LOG = "test.results.log";
    private static final String TEST_LOG_FILE = "greengrass-test-run.log";

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

        RuntimeOptionsBuilder optionsBuilder = new RuntimeOptionsBuilder()
                .addFeature(FeatureWithLines.parse(DEFAULT_FEATURES))
                .addGlue(GluePath.parse(DEFAULT_GLUE_PATH))
                .addPluginName(StepTrackingReporting.class.getName(), true);
        String tags = System.getProperty("tags");
        if (Objects.nonNull(tags)) {
            optionsBuilder.addTagFilter(tags);
        }

        if (Boolean.parseBoolean(System.getProperty(TEST_RESULTS_XML, "true"))) {
            final Path resultsXml = output.toAbsolutePath().resolve("TEST-greengrass-results.xml");
            optionsBuilder.addPluginName("junit:" + resultsXml, true);
        }

        // Allow external feature files. This enables framework features to work with static features.
        Optional.ofNullable(System.getProperty(FEATURE_PATH)).ifPresent(featurePath -> {
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get(featurePath), "*.feature")) {
                paths.forEach(path -> optionsBuilder.addFeature(FeatureWithLines.parse("file:" + path)));
            } catch (NotDirectoryException nde) {
                optionsBuilder.addFeature(FeatureWithLines.parse("file:" + featurePath));
            } catch (IOException ie) {
                LOGGER.warn("Failed to select features in {}:", featurePath, ie);
            }
        });

        Runtime runtime = Runtime.builder()
                .withRuntimeOptions(optionsBuilder.build())
                .build();
        runtime.run();
        int exitStatus = runtime.exitStatus();
        if (exitStatus != 0) {
            System.out.println("Scenario tests failed.");
        }
        System.exit(runtime.exitStatus());
    }


    /**
     * Update the logger with a file appender so it can be reviewed outside of console output.
     *
     * @param output the output path to place the log file
     */
    private static void addFileAppender(final Path output) {
        final Level level = Level.valueOf(System.getProperty(LOG_LEVEL, "info"));
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig config = context.getConfiguration().getRootLogger();
        if (Boolean.parseBoolean(System.getProperty(TEST_RESULTS_LOG, "false"))) {
            final Layout layout = PatternLayout.newBuilder().withPattern(
                    "%d{yyyy-MMM-dd HH:mm:ss,SSS} [%X{feature}] [%X{testId}] [%level] %logger{36} - %msg%n").build();
            FileAppender.Builder appenderBuilder = FileAppender.newBuilder()
                    .withFileName(output.resolve(TEST_LOG_FILE).toString())
                    .withImmediateFlush(true)
                    .withBufferedIo(false);
            appenderBuilder.setLayout(layout).setName("File");
            Appender appender = appenderBuilder.build();
            appender.start();
            config.addAppender(appender, level, null);
        }
        config.setLevel(level);
        context.updateLoggers();
    }
}
