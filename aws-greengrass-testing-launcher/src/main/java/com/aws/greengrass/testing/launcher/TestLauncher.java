/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.ParameterValue;
import com.aws.greengrass.testing.launcher.reporting.StepTrackingReporting;
import com.aws.greengrass.testing.launcher.utils.CucumberReportUtils;
import com.aws.greengrass.testing.modules.FeatureParameters;
import com.aws.greengrass.testing.modules.GreengrassInjectorSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

public final class TestLauncher {
    private static final Logger LOGGER = LogManager.getLogger(TestLauncher.class);
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "classpath:greengrass/features";
    private static final String TEST_LOG_FILE = "greengrass-test-run.log";
    private static final String CUCUMBER_REPORT = "cucumber.json";
    private static final String TEST_RESULTS_XML_FILE = "TEST-greengrass-results.xml";
    private ObjectMapper mapper;

    @Inject
    public TestLauncher(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static CommandLine.Model.CommandSpec createCommandSpec() {
        CommandLine.Model.CommandSpec commandSpec = CommandLine.Model.CommandSpec.create();
        commandSpec.name("gg-test").version("v1.0.0").mixinStandardHelpOptions(true);
        ParameterValues defaultValues = ParameterValues.createDefault();
        Parameters.loadAll().sorted().forEach(parameter -> {
            commandSpec.addOption(CommandLine.Model.OptionSpec
                    .builder("--" + parameter.name().replace(".", "-"))
                    .negatable(parameter.flag())
                    .type(parameter.flag() ? Boolean.class : String.class)
                    .description(parameter.description())
                    .required(parameter.required())
                    .paramLabel(parameter.name())
                    .defaultValue("") // Needed to trigger the pre-processor
                    .preprocessor((stack, commandSpec12, argSpec, map) -> {
                        defaultValues.getString(argSpec.paramLabel()).ifPresent(stack::push);
                        return false;
                    })
                    .parameterConsumer((stack, argSpec, commandSpec1) -> {
                        if (!stack.empty()) {
                            String value = stack.pop();
                            if (!value.isEmpty()) {
                                TestLauncherParameterValues.put(parameter.name(), ParameterValue.of(value));
                            }
                        }
                    })
                    .build());
        });
        return commandSpec;
    }

    /**
     * Start the {@link TestLauncher} wrapping a Cucumber platform engine.
     *
     * @param args optional additional arguments for the runner
     * @throws Exception any runtime failure before Cucumber runner begins
     */
    public static void main(String[] args) throws Exception {
        Injector injector = new GreengrassInjectorSource().getInjector();
        TestLauncher launcher = injector.getInstance(TestLauncher.class);

        CommandLine cli = new CommandLine(createCommandSpec());
        CommandLine.ParseResult parseResult = cli.parseArgs(args);
        if (CommandLine.printHelpIfRequested(parseResult)) {
            System.exit(0);
        }

        List<String> uriPool = null;
        final ParameterValues values = TestLauncherModule.providesTestLauncherValues();
        final String parallelConfig = values.getString(TestLauncherParameters.PARALLEL_CONFIG).orElse("");

        if (!parallelConfig.isEmpty()) {
            // load parallel config
            ParallelizationConfig parallelizationConfig = launcher.mapper.readValue(parallelConfig,
                    ParallelizationConfig.class);

            // dry-run
            runTests(values, true, uriPool);

            // parse cucumber report to get set of tests to run
            final CucumberReportUtils cucumberReportUtils = TestLauncherModule.providesCucumberUtils();
            uriPool = cucumberReportUtils.parseDryRunCucumberReport(parallelizationConfig, values);
        }

        //actual run
        runTests(values, false, uriPool);
    }

    private static void runTests(ParameterValues values, boolean dryRun, List<String> uriPool) throws IOException {
        final Path output = Paths.get(values.getString(FeatureParameters.TEST_RESULTS_PATH).orElse(""));
        Files.createDirectories(output);
        addFileAppender(values, output);

        RuntimeOptionsBuilder optionsBuilder = new RuntimeOptionsBuilder()
                .addGlue(GluePath.parse(DEFAULT_GLUE_PATH))
                .setStrict(true)
                .setDryRun(dryRun)
                .addPluginName(StepTrackingReporting.class.getName(), true);

        if (uriPool == null || uriPool.isEmpty()) {
            optionsBuilder.addFeature(FeatureWithLines.parse(DEFAULT_FEATURES));
        } else {
            for (String uri : uriPool) {
                optionsBuilder.addFeature(FeatureWithLines.parse(uri));
            }
        }

        values.getString(TestLauncherParameters.TAGS).ifPresent(tags -> {
            if (!tags.contains("@")) {
                // Assuming JUnit style tags being supplied here
                final Matcher matcher = Pattern.compile("([A-Za-z0-9_\\.]+)").matcher(tags);
                tags = matcher.replaceAll("@$1")
                        .replace("&", " and ")
                        .replace("|", " or ");
            }
            optionsBuilder.addTagFilter(tags);
        });

        values.getString(TestLauncherParameters.ADDITIONAL_PLUGINS).ifPresent(plugins -> {
            for (String plugin : plugins.split("\\s*,\\s*")) {
                optionsBuilder.addPluginName(plugin, true);
            }
        });

        if (values.getBoolean(TestLauncherParameters.TEST_RESULTS_XML).orElse(true)) {
            final Path resultsXml = output.toAbsolutePath().resolve(TEST_RESULTS_XML_FILE);
            optionsBuilder.addPluginName("junit:" + resultsXml, true);
        }

        if (values.getBoolean(TestLauncherParameters.TEST_RESULTS_JSON).orElse(true)) {
            final Path resultsJson = output.toAbsolutePath().resolve(CUCUMBER_REPORT);
            optionsBuilder.addPluginName("json:" + resultsJson, true);
        }

        // Allow external feature files. This enables framework features to work with static features.
        values.getString(TestLauncherParameters.FEATURE_PATH).ifPresent(featurePath -> {
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

        if (!dryRun) {
            System.exit(runtime.exitStatus());
        }
    }

    /**
     * Update the logger with a file appender so it can be reviewed outside of console output.
     *
     * @param values the parsed {@link ParameterValues}
     * @param output the output path to place the log file
     */
    private static void addFileAppender(final ParameterValues values, final Path output) {
        final Level level = Level.valueOf(values.getString(TestLauncherParameters.LOG_LEVEL).orElse("info"));
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig config = context.getConfiguration().getRootLogger();
        if (values.getBoolean(TestLauncherParameters.TEST_RESULTS_LOG).orElse(false)) {
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
