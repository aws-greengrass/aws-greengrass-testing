/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.parameter;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ParameterValue;
import com.google.auto.service.AutoService;

import java.util.Optional;

@AutoService(ParameterValues.class)
public class SystemPropertiesParameterValues implements ParameterValues {
    @Override
    public Optional<ParameterValue> get(String name) {
        return Optional.ofNullable(System.getProperty(name)).map(ParameterValue::of);
    }
}
