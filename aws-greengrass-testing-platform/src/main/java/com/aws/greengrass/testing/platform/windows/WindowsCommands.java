package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;

public class WindowsCommands implements Commands {
    private final Device device;

    WindowsCommands(final Device device) {
        this.device = device;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        return device.execute(CommandInput.builder()
                .from(input)
                .line("cmd.exe /c" + input.line())
                .build());
    }
}
