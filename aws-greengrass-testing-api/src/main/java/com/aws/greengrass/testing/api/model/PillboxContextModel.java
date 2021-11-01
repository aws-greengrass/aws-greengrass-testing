/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.nio.file.Paths;

@TestingModel
@Value.Immutable
interface PillboxContextModel {
    @Value.Default
    default String binaryName() {
        return "pillbox.jar";
    }

    Path onHost();

    @Value.Default
    default Path onDevice() {
        return Paths.get(binaryName());
    }
}
