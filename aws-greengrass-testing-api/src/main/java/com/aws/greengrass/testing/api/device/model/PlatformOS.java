/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.model;

import org.immutables.value.Value;

@Value.Style(jdkOnly = true)
@Value.Immutable
public abstract class PlatformOS {
    public abstract String name();

    public abstract String arch();

    public boolean isWindows() {
        return name().toLowerCase().contains("wind");
    }

    public static ImmutablePlatformOS.Builder builder() {
        return ImmutablePlatformOS.builder();
    }

    public static PlatformOS currentPlatform() {
        return PlatformOS.builder().name(System.getProperty("os.name")).arch(System.getProperty("os.arch")).build();
    }
}
