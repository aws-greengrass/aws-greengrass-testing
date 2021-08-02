/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.api.util.FileUtils;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@TestingModel
@Value.Immutable
interface TestContextModel extends Closeable {
    TestId testId();

    Path testDirectory();

    Path testResultsPath();

    CleanupContext cleanupContext();

    InitializationContext initializationContext();

    String logLevel();

    Path installRoot();

    String currentUser();

    String coreThingName();

    @Override
    default void close() throws IOException {
        if (!cleanupContext().persistGeneratedFiles()) {
            FileUtils.recursivelyDelete(testDirectory());
        }
    }
}
