/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.ParameterValue;

import java.util.Optional;

public interface ParameterValues {
    Optional<ParameterValue> get(String name);

    default Optional<String> getString(String name) {
        return get(name).map(ParameterValue::value);
    }
}
