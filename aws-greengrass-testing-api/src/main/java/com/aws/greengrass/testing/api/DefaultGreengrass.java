package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;

public class DefaultGreengrass implements Greengrass {
    private final Device device;

    public DefaultGreengrass(final Device device) {
        this.device = device;
    }

    @Override
    public void start() {
        device.execute(CommandInput.builder()
                .line("java").addArgs("-jar", "lib/Greengrass.jar")
                .build());
    }

    @Override
    public void stop() {
    }
}
