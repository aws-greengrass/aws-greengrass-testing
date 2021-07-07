/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Objects;
import javax.annotation.Nonnull;

@TestingModel
@Value.Immutable
interface TimeoutMultiplierModel {
    @Value.Default
    default double multiplier() {
        return 1.0;
    }

    default long multiply(@Nonnull Number value) {
        return Math.round(multiplier() * value.doubleValue());
    }
}
