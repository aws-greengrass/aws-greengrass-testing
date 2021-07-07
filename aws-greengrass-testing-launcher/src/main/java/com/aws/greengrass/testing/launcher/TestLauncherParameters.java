/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.Parameter;

import java.util.Arrays;
import java.util.List;

public class TestLauncherParameters implements Parameters {
    static final String LOG_LEVEL = "log.level";
    static final String FEATURE_PATH = "feature.path";
    static final String TEST_RESULTS_XML = "test.results.xml";
    static final String TEST_RESULTS_LOG = "test.results.log";

    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.of(LOG_LEVEL, "Log level of the test run. Defaults to \"INFO\"."),
                Parameter.of(FEATURE_PATH, "File or directory containing additional feature files. "
                        + "Default is no additional feature files are used."),
                Parameter.of(TEST_RESULTS_XML, "Flag to determine if a resulting JUnit XML report is "
                        + "generated written to disk. Defaults to true."),
                Parameter.of(TEST_RESULTS_LOG, "Flag to determine if the JUnit console output is "
                        + "generated written to disk, Defaults to false.")
        );
    }
}
