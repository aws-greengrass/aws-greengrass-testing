package com.aws.greengrass.testing.api.device;



import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public interface Device {
    String id();

    String type();

    PlatformOS platform();

    byte[] execute(CommandInput input) throws CommandExecutionException;

    default String executeToString(CommandInput input) throws CommandExecutionException {
        return new String (execute(input), StandardCharsets.UTF_8);
    }

    void copy(Path source, Path destination) throws CopyException;

    boolean exists(Path file);

    static Device acquire(String type) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        ServiceLoader.load(Device.class).iterator(),
                        Spliterator.DISTINCT), false)
                .filter(device -> device.type().equals(type))
                .findFirst()
                .orElseGet(LocalDevice::new);
    }
}
