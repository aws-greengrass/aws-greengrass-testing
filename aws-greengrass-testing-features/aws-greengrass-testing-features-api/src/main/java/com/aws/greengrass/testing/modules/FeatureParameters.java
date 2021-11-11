/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.Parameter;
import com.aws.greengrass.testing.api.model.PersistMode;
import com.google.auto.service.AutoService;

import java.util.Arrays;
import java.util.List;

@AutoService(Parameters.class)
public class FeatureParameters implements Parameters {
    static final String DEVICE_MODE = "device.mode";
    static final String NUCLEUS_VERSION = "ggc.version";
    static final String NUCLEUS_ARCHIVE_PATH = "ggc.archive";
    static final String NUCLEUS_LOG_LEVEL = "ggc.log.level";
    static final String NUCLEUS_INSTALL_ROOT = "ggc.install.root";
    static final String NUCLEUS_USER = "ggc.user.name";
    static final String TIMEOUT_MULTIPLIER = "timeout.multiplier";
    static final String TEST_TEMP_PATH = "test.temp.path";
    static final String TEST_RESULTS_PATH = "test.log.path";
    static final String TEST_ID_PREFIX = "test.id.prefix";
    static final String TES_ROLE_NAME = "ggc.tes.rolename";

    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.of(DEVICE_MODE, "The target device under test. Defaults to local device."),
                Parameter.of(NUCLEUS_VERSION, "Overrides the version of the running Greengrass "
                        + "Nucleus component. Defaults to the value found in " + NUCLEUS_ARCHIVE_PATH),
                Parameter.builder()
                        .name(NUCLEUS_ARCHIVE_PATH)
                        .description("The path to the archived Greengrass Nucleus component.")
                        .build(),
                Parameter.of(NUCLEUS_LOG_LEVEL, "Set the Greengrass Nucleus log level for the test run. "
                        + "Default is \"INFO\""),
                Parameter.of(NUCLEUS_INSTALL_ROOT, "Directory to install the Greengrass Nucleus. "
                        + "Defaults to " + TEST_TEMP_PATH + " and test run folder"),
                Parameter.of(NUCLEUS_USER, "The user:group posixUser value for the Greengrass Nucleus. "
                        + "Defaults to " + System.getProperty("user.name")),
                Parameter.of(TIMEOUT_MULTIPLIER, "Multiplier provided to all test timeouts. Default is 1.0"),
                Parameter.of(TEST_TEMP_PATH, "Directory to generate local test artifacts. "
                        + "Defaults to a random temp directory prefixed with gg-testing."),
                Parameter.of(TEST_RESULTS_PATH, "Directory that will contain the results of the "
                        + "entire test run. Defaults to \"testResults\"."),
                Parameter.of(TEST_ID_PREFIX, "A common prefix applied to all test specific resources "
                        + "including AWS resource names and tags. Default is a \"gg\" prefix."),
                Parameter.of(TES_ROLE_NAME, "The Iam Role that ggc will assume to access AWS services"
                        + "If a role with given name does not exist then one will be created and default access "
                        + "policy")
        );
    }
}
