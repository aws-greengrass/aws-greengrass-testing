/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.api.model.PillboxContext;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class RemoteFiles implements PlatformFiles, UnixPathsMixin {
    private final PlatformOS host;
    private final Device device;
    private final PillboxContext pillboxContext;

    /**
     * Interacts with a remote file system using the pillbox binary.
     *
     * @param device the {@link Device} representing a remote device
     * @param pillboxContext the {@link PillboxContext} containing where the binary is located
     */
    public RemoteFiles(final Device device, final PillboxContext pillboxContext) {
        this.host = PlatformOS.currentPlatform();
        this.device = device;
        this.pillboxContext = pillboxContext;
    }

    private byte[] files(String command, String...args) {
        return device.execute(CommandInput.builder()
                .line("java")
                .addArgs("-jar", pillboxContext.onDevice().toString(), "files", command)
                .addArgs(args)
                .build());
    }

    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        return files("cat", format(filePath));
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        files("rm", "-r", format(filePath));
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        files("mkdir", "-p", format(filePath));
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        final byte[] output = files("find", "-type", "f", format(filePath));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(output)))) {
            return reader.lines().map(Paths::get).map(path -> Paths.get(format(path))).collect(Collectors.toList());
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.builder()
                    .line("files").addArgs("find", "-type", "f", format(filePath))
                    .build());
        }
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        device.copyTo(source.toAbsolutePath().toString(), format(destination));
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        try {
            files("exists", format(filePath));
            return true;
        } catch (CommandExecutionException executionException) {
            return false;
        }
    }

    @Override
    public String format(Path filePath) {
        if (host.isWindows() || device.platform().isWindows()) {
            return formatToUnixPath(filePath.toString());
        }
        return filePath.toString();
    }
}
