/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.ParameterValue;
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
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestLauncher {
    private static final Logger LOGGER = LogManager.getLogger(TestLauncher.class);
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "classpath:greengrass/features";
    private static final String LOG_LEVEL = "log.level";
    private static final String FEATURE_PATH = "feature.path";
    private static final String TEST_RESULTS_XML = "test.results.xml";
    private static final String TEST_RESULTS_LOG = "test.results.log";
    private static final String TEST_LOG_FILE = "greengrass-test-run.log";

    @CommandLine.Command(
            name = "greengrass-testing",
            header = "Run end to end feature tests on a Greengrass platform.",
            version = "v1.0.0", mixinStandardHelpOptions = true)
    public static final class Run {
        @CommandLine.Option(
                names = "--feature-path",
                description = "File or directory containing additional feature files. Default is no additional "
                        + "feature files are used.")
        private String featurePath;

        @CommandLine.Option(
                names = "--tags",
                description = "Only run feature tags.")
        private String tags;

        @CommandLine.Option(
                names = "--test-results-xml",
                description = "Flag to determine if a resulting JUnit XML report is generated written to disk. "
                        + "Defaults to true.",
                negatable = true)
        private boolean testResultsXml = true;

        @CommandLine.Option(
                names = "--test-results-log",
                description = "Flag to determine if the JUnit console output is generated written to disk. "
                        + "Defaults to false.",
                negatable = true)
        private boolean testResultsLog = false;

        @CommandLine.Option(
                names = "--log-level",
                description = "Log level of the test run. Defaults to \"INFO\"",
                defaultValue = "INFO")
        private String logLevel;
    }

    /**
     * Start the {@link TestLauncher} wrapping a Cucumber platform engine.
     *
     * @param args optional additional arguments for the runner
     * @throws Exception any runtime failure before Cucumber runner begins
     */
    public static void main(String[] args) throws Exception {
        Run run = new Run();
        CommandLine cli = new CommandLine(run);
        CommandLine.Model.CommandSpec commandSpec = cli.getCommandSpec();
        Parameters.loadAll().forEach(parameter -> {
            commandSpec.addOption(CommandLine.Model.OptionSpec
                    .builder("--" + parameter.name().replace(".", "-"))
                    .type(String.class)
                    .description(parameter.description())
                    .required(parameter.required())
                    .paramLabel(parameter.name())
                    .parameterConsumer(new CommandLine.IParameterConsumer() {
                        @Override
                        public void consumeParameters(Stack<String> stack, CommandLine.Model.ArgSpec argSpec,
                                                      CommandLine.Model.CommandSpec commandSpec) {
                            if (!stack.empty()) {
                                String value = stack.pop();
                                TestLauncherParameterValues.put(parameter.name(), ParameterValue.of(value));
                            }
                        }
                    })
                    .build());
        });
        CommandLine.ParseResult parseResult = cli.parseArgs(args);
        if (CommandLine.printHelpIfRequested(parseResult)) {
            cli.usage(System.out);
            System.exit(0);
        }
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
            if (!tags.contains("@")) {
                // Assuming JUnit style tags being supplied here
                final Matcher matcher = Pattern.compile("([A-Za-z0-9_\\.]+)").matcher(tags);
                tags = matcher.replaceAll("@$1")
                        .replace("&", " and ")
                        .replace("|", " or ");
            }
            optionsBuilder.addTagFilter(tags);
        }

        if (Boolean.parseBoolean(System.getProperty(TEST_RESULTS_XML, "true"))) {
            final Path resultsXml = output.toAbsolutePath().resolve("TEST-greengrass-results.xml");
            optionsBuilder.addPluginName("junit:" + resultsXml, true);
        }

        // Allow external feature files. This enables framework features to work with static features.
        Optional.ofNullable(System.getProperty(FEATURE_PATH)).ifPresent(featurePath -> {
            final List<String> selectedFiles = new ArrayList<>();
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
            LOGGER.error("Scenario tests failed.");
        }
        System.exit(runtime.exitStatus());
    }


    /**
     * Update the logger with a file appender so it can be reviewed outside of console output.
     *
     * @param run the parsed {@link Run} object
     * @param output the output path to place the log file
     */
    private static void addFileAppender(final Run run, final Path output) {
        final Level level = Level.valueOf(run.logLevel);
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig config = context.getConfiguration().getRootLogger();
        if (run.testResultsLog) {
            final Layout layout = PatternLayout.newBuilder().withPattern(
                    "%d{yyyy-MMM-dd HH:mm:ss,SSS} [%X{feature}] [%X{testId}] [%level] %logger{36} - %msg%n").build();
            FileAppender.Builder appenderBuilder = FileAppender.newBuilder()
                    .withFileName(output.resolve(TEST_LOG_FILE).toString())
                    .withImmediateFlush(true)
                    .withBufferedIo(true);
            appenderBuilder.setLayout(layout).setName("File");
            Appender appender = appenderBuilder.build();
            appender.start();
            config.addAppender(appender, level, null);
        }
        config.setLevel(level);
        context.updateLoggers();
    }
}
