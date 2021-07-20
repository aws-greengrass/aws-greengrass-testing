/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import io.cucumber.guice.ScenarioScoped;

import java.util.Map;

@AutoService(Module.class)
public class DeviceModule extends AbstractModule {

    @Provides
    @ScenarioScoped
    static Device providesDevice(Map<String, Device> devicePool, ParameterValues parameterValues) {
        return devicePool.get(parameterValues.getString(FeatureParameters.DEVICE_MODE).orElse("LOCAL"));
    }

    @Singleton
    @ProvidesIntoMap
    @StringMapKey("LOCAL")
    static Device providesLocalDevice(TimeoutMultiplier multiplier) {
        return new LocalDevice(multiplier);
    }
}
