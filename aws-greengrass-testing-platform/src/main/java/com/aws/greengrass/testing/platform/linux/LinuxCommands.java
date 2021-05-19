package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;

public class LinuxCommands implements Commands {
    private final Device device;

    LinuxCommands(final Device device) {
        this.device = device;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        return device.execute(CommandInput.builder()
                .from(input)
                .line("sh -c " + input.line())
                .build());
    }
}
