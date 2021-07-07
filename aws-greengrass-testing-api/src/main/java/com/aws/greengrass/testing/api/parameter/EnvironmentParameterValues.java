/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.parameter;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ParameterValue;

import java.util.Optional;

public class EnvironmentParameterValues implements ParameterValues {
    @Override
    public Optional<ParameterValue> get(String name) {
        return Optional.ofNullable(System.getenv(name.replace(".", "_").toUpperCase()))
                .map(ParameterValue::of);
    }
}
