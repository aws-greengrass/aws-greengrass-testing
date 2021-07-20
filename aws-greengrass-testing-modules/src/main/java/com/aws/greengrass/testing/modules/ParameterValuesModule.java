/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.parameter.CompositeParameterValues;
import com.aws.greengrass.testing.api.parameter.EnvironmentParameterValues;
import com.aws.greengrass.testing.api.parameter.SystemPropertiesParameterValues;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;

import java.util.Set;
import javax.inject.Singleton;

@AutoService(Module.class)
public class ParameterValuesModule extends AbstractModule {
    @Singleton
    @Provides
    static ParameterValues providesCompositeParameterValues(Set<ParameterValues> parameterValues) {
        return new CompositeParameterValues(parameterValues);
    }

    @Singleton
    @ProvidesIntoSet
    static ParameterValues providesSystemParameterValues() {
        return new SystemPropertiesParameterValues();
    }

    @Singleton
    @ProvidesIntoSet
    static ParameterValues providesEnvironmentParameterValues() {
        return new EnvironmentParameterValues();
    }
}
