/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ParameterValue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class TestLauncherParameterValues implements ParameterValues {
    private static  final Map<String, ParameterValue> PARAMETER_VALUE_MAP = new ConcurrentHashMap<>();

    @Override
    public Optional<ParameterValue> get(String name) {
        return Optional.ofNullable(PARAMETER_VALUE_MAP.get(name));
    }

    static void put(String name, ParameterValue value) {
        PARAMETER_VALUE_MAP.put(name, value);
    }
}
