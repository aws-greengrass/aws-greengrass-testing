/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.parameter;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ParameterValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;

public class CompositeParameterValues implements ParameterValues {
    private static final Logger LOGGER = LogManager.getLogger(CompositeParameterValues.class);
    private final Set<ParameterValues> parameterValues;

    public CompositeParameterValues(final Set<ParameterValues> parameterValues) {
        this.parameterValues = parameterValues;
    }

    @Override
    public Optional<ParameterValue> get(String name) {
        for (ParameterValues values : parameterValues) {
            final Optional<ParameterValue> value = values.get(name);
            if (value.isPresent()) {
                LOGGER.debug("Parameter value for {} was found with {}: {}",
                        name, values.getClass().getSimpleName(), value);
                return value;
            }
        }
        return Optional.empty();
    }
}
