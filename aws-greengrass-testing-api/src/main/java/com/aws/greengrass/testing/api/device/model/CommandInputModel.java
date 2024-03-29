/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface CommandInputModel {
    String line();

    @Nullable
    Path workingDirectory();

    @Nullable
    byte[] input();

    @Nullable
    Long timeout();

    @Nullable
    List<String> args();
}
