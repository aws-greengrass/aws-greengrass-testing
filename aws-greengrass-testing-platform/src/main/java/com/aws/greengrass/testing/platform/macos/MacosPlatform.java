package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.Platform;
import com.google.auto.service.AutoService;

@AutoService(Platform.class)
public class MacosPlatform implements Platform {
    private final Device device;

    public MacosPlatform(final Device device) {
        this.device = device;
    }

    public MacosCommands commands() {
        return new MacosCommands(device);
    }
}
