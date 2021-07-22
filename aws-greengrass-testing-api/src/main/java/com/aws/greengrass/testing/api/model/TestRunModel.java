/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.time.Duration;
import java.util.UUID;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface TestRunModel {
    UUID uuid();

    String name();

    @Nullable
    Duration duration();

    @Nullable
    String message();

    @Value.Default
    default boolean skipped() {
        return false;
    }

    @Value.Default
    default boolean failed() {
        return false;
    }

    @Value.Default
    default boolean passed() {
        return false;
    }
}
