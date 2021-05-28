package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.DefaultGreengrass;
import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.Device;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

@AutoService(Module.class)
public class GreengrassModule extends AbstractModule {
    @Provides
    @ScenarioScoped
    static Greengrass providesGreengrass(final Device device) {
        return new DefaultGreengrass(device);
    }
}
