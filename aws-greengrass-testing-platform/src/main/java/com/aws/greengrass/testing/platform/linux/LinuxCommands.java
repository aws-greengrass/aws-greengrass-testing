package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.UnixCommands;

public class LinuxCommands extends UnixCommands {
    public LinuxCommands(final Device device) {
        super(device);
    }
}
