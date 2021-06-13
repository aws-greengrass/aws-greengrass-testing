package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import io.cucumber.guice.ScenarioScoped;

import javax.inject.Named;
import java.util.Map;

@AutoService(Module.class)
public class DeviceModule extends AbstractModule {
    private static final String DEVICE_MODE = "device.mode";

    @Provides
    @ScenarioScoped
    static Device providesDevice(Map<String, Device> devicePool) {
        return devicePool.getOrDefault(System.getProperty(DEVICE_MODE, "LOCAL"), providesLocalDevice());
    }

    @Singleton
    @ProvidesIntoMap
    @StringMapKey("LOCAL")
    static Device providesLocalDevice() {
        return new LocalDevice();
    }
}