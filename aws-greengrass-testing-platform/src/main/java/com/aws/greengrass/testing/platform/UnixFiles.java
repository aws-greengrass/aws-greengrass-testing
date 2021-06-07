package com.aws.greengrass.testing.platform;


import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class UnixFiles implements PlatformFiles {
    protected final Commands commands;

    public UnixFiles(Commands commands) {
        this.commands = commands;
    }

    @Override
    public byte[] readBytes(final Path filePath) throws CommandExecutionException {
        return commands.execute(CommandInput.of("cat " + filePath.toString()));
    }

    @Override
    public List<Path> listContents(final Path filePath) throws CommandExecutionException {
        final String[] files = commands.executeToString(CommandInput.builder()
                .line("find").addArgs(filePath.toString(), "-type", "f")
                .build())
                .split("\\r?\\n");
        return Arrays.stream(files)
                .map(String::trim)
                .filter(file -> !file.isEmpty())
                .map(Paths::get)
                .collect(Collectors.toList());
    }
}
