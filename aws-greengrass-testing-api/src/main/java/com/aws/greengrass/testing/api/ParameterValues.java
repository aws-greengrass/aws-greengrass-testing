/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.ParameterValue;
import com.aws.greengrass.testing.api.parameter.CompositeParameterValues;

import java.util.HashSet;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

public interface ParameterValues {
    Optional<ParameterValue> get(String name);

    default Optional<String> getString(String name) {
        return get(name).map(ParameterValue::value);
    }

    /**
     * Creates a {@link CompositeParameterValues} with any {@link ParameterValues} found on the classpath.
     *
     * @return
     */
    static ParameterValues createDefault() {
        ServiceLoader<ParameterValues> systemValues = ServiceLoader.load(ParameterValues.class);
        final Set<ParameterValues> set = new HashSet<>();
        systemValues.iterator().forEachRemaining(set::add);
        return new CompositeParameterValues(set);
    }
}
