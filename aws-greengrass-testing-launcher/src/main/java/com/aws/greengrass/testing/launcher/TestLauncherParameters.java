/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.Parameter;
import com.google.auto.service.AutoService;

import java.util.Arrays;
import java.util.List;

@AutoService(Parameters.class)
public class TestLauncherParameters implements Parameters {
    static final String TAGS = "tags";
    static final String LOG_LEVEL = "log.level";
    static final String FEATURE_PATH = "feature.path";
    static final String TEST_RESULTS_LOG = "test.results.log";
    static final String TEST_RESULTS_XML = "test.results.xml";
    static final String TEST_RESULTS_JSON = "test.results.json";
    static final String ADDITIONAL_PLUGINS = "additional.plugins";
    static final String PARALLEL_CONFIG = "parallel.config";
    public static final String TEST_RESULTS_PATH = "test.log.path";

    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.of(TAGS, "Only run feature tags. Can be intersected with '&'"),
                Parameter.of(FEATURE_PATH, "File or directory containing additional feature files. "
                        + "Default is no additional feature files are used."),
                Parameter.builder()
                        .name(TEST_RESULTS_LOG)
                        .description("Flag to determine if the console output is generated written to disk. "
                                + "Defaults to false.")
                        .build(),
                Parameter.builder()
                        .name(TEST_RESULTS_XML)
                        .description("Flag to determine if a resulting JUnit XML report is generated written to disk. "
                                + "Defaults to true.")
                        .build(),
                Parameter.builder()
                        .name(TEST_RESULTS_JSON)
                        .description("Flag to determine if a resulting cucumber json report is generated written to "
                                + "disk. Defaults to true.")
                        .build(),
                Parameter.of(LOG_LEVEL, "Log level of the test run. Defaults to \"INFO\""),
                Parameter.of(ADDITIONAL_PLUGINS, "Optional additional Cucumber plugins."),
                Parameter.of(PARALLEL_CONFIG, "Set of batch index and number of batches as a JSON String. "
                        + "Default value of batch index is 0 and number of batches is 1."),
                Parameter.of(TEST_RESULTS_PATH, "Directory that will contain the results of the "
                        + "entire test run. Defaults to \"testResults\".")
        );
    }
}
