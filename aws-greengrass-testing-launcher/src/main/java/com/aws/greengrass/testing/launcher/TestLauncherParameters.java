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
    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.of("tags", "Only run feature tags. Can be intersected with '&'"),
                Parameter.of("feature.path", "File or directory containing additional feature files. "
                        + "Default is no additional feature files are used."),
                Parameter.builder()
                        .name("test.results.log")
                        .description("Flag to determine if the console output is generated written to disk. "
                                + "Defaults to false.")
                        .build(),
                Parameter.builder()
                        .name("test.results.xml")
                        .description("Flag to determine if a resulting JUnit XML report is generated written to disk. "
                                + "Defaults to true.")
                        .build(),
                Parameter.of("log.level", "Log level of the test run. Defaults to \"INFO\"")
        );
    }
}
