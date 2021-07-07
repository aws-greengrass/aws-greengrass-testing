/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.List;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface ParameterModel extends Comparable<ParameterModel> {
    String name();

    String description();

    @Value.Default
    default boolean flag() {
        return false;
    }

    @Nullable
    List<String> possibleValues();

    @Value.Default
    default boolean required() {
        return false;
    }

    @Override
    default int compareTo(ParameterModel parameterModel) {
        int requiredCompare = Boolean.compare(required(), parameterModel.required());
        if (requiredCompare == 0) {
            return name().compareTo(parameterModel.name());
        } else {
            return requiredCompare;
        }
    }
}
