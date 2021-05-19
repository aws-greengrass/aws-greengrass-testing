package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.Platform;
import com.google.auto.service.AutoService;

@AutoService(Platform.class)
public class LinuxPlatform implements Platform {
    private final Device device;

    public LinuxPlatform(final Device device) {
        this.device = device;
    }

    public LinuxCommands commands() {
        return new LinuxCommands(device);
    }
}
