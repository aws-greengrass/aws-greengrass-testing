/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import io.cucumber.guice.ScenarioScoped;
import software.amazon.awssdk.utils.IoUtils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@AutoService(Module.class)
public class DeviceModule extends AbstractModule {
    private static final Set<String> PILLBOX_PLACED = new ConcurrentSkipListSet<>();
    private static final String RESOURCE_PATH = "/greengrass/platform/artifacts/pillbox.jar";

    // TODO: move this into a module that is optional, and can be included for remote DUTs
    @Provides
    @Singleton
    static PillboxContext providesPillboxContext(final GreengrassContext greengrassContext) {
        final Path extractionPath = greengrassContext.tempDirectory().resolve("pillbox.jar");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(extractionPath.toFile()));
             InputStream input = DeviceModule.class.getResourceAsStream(RESOURCE_PATH)) {
            IoUtils.copy(input, output);
        } catch (IOException ie) {
            throw new ModuleProvisionException(ie);
        }
        // Can provide a parameter to place in a specific place.
        return PillboxContext.builder()
                .onHost(extractionPath)
                .build();
    }

    @Provides
    @ScenarioScoped
    static Device providesDevice(
            final Map<String, Device> devicePool,
            final ParameterValues parameterValues,
            final PillboxContext pillboxContext) {
        final Device device = devicePool
                .get(parameterValues.getString(FeatureParameters.DEVICE_MODE).orElse(LocalDevice.TYPE));
        if (!device.type().equals(LocalDevice.TYPE) && PILLBOX_PLACED.add(device.id())) {
            device.copyTo(pillboxContext.onHost().toAbsolutePath().toString(), pillboxContext.onDevice().toString());
        }
        return device;
    }

    @Singleton
    @ProvidesIntoMap
    @StringMapKey(LocalDevice.TYPE)
    static Device providesLocalDevice(TimeoutMultiplier multiplier) {
        return new LocalDevice(multiplier);
    }
}
