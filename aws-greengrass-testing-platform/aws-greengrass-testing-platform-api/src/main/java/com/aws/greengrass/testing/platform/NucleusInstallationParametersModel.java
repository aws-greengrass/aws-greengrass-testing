/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
public abstract class NucleusInstallationParametersModel {
    abstract Map<String, String> getSystemProperties();

    abstract Map<String, String> getGreengrassParameters();

    @Nullable
    abstract List<String> getJvmArguments();

    abstract Path getGreengrassRootDirectoryPath();

}
