/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.api.util.FileUtils;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

@TestingModel
@Value.Immutable
interface GreengrassContextModel extends Closeable {
    String version();

    Path tempDirectory();

    CleanupContext cleanupContext();

    default Path greengrassPath() {
        return tempDirectory().resolve("greengrass");
    }

    @Override
    default void close() throws IOException {
        if (!cleanupContext().persistGeneratedFiles()) {
            FileUtils.recursivelyDelete(tempDirectory());
        }
    }
}
