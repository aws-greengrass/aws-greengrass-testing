package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.device.Device;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

@AutoService(Module.class)
public class DeviceModule extends AbstractModule {
    private static final String DEVICE_MODE = "device.mode";

    @Provides
    @ScenarioScoped
    static Device providesDevice() {
        return Device.acquire(System.getProperty(DEVICE_MODE));
    }
}
