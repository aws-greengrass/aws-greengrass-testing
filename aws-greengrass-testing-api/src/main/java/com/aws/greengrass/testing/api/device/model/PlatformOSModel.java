/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface PlatformOSModel {
    String name();

    String arch();

    default boolean isWindows() {
        return name().toLowerCase().contains("wind");
    }
}
