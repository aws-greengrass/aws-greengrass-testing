package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

public interface Commands {
    byte[] execute(CommandInput input) throws CommandExecutionException;
}
