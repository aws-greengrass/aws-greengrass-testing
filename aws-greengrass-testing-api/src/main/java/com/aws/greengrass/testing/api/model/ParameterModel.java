/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface ParameterModel {
    String name();

    String description();

    @Value.Default
    default boolean required() {
        return false;
    }
}
