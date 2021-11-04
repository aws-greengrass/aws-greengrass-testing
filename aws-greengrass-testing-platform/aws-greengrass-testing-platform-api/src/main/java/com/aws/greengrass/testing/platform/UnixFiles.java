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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class UnixFiles implements PlatformFiles, UnixPathsMixin {
    protected final Commands commands;
    private final Device device;
    private final PlatformOS host;

    /**
     * A unix based platform. All commands and paths are formatted appropriately.
     *
     * @param commands the {@link Commands} interface this platform will interact with
     * @param device the underlying {@link Device} this platform interacts with
     */
    public UnixFiles(Commands commands, Device device) {
        this.host = PlatformOS.currentPlatform();
        this.device = device;
        this.commands = commands;
    }

    @Override
    public PlatformOS host() {
        return host;
    }

    @Override
    public byte[] readBytes(final Path filePath) throws CommandExecutionException {
        System.out.println("reading bytes from file path " + filePath.toString());
        byte[] readBytes = commands.execute(CommandInput.of("cat " + filePath.toString()));
        System.out.println("Bytes read are " + new String(readBytes, StandardCharsets.UTF_8));
        return readBytes;
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        commands.execute(CommandInput.of("rm -rf " + filePath.toString()));
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        commands.execute(CommandInput.of("mkdir -p " + filePath.toString()));
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        return device.exists(formatToUnixPath(filePath.toString()));
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        device.copyTo(source.toAbsolutePath().toString(), formatToUnixPath(destination.toString()));
    }

    @Override
    public String format(Path filePath) {
        return formatToUnixPath(filePath.toString());
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

    @Override
    public void writeBytes(Path filePath, byte[] bytes) {
        System.out.println("Writing bytes to the file path " + filePath.toString());
        System.out.println("Bytes written : " + new String(bytes, StandardCharsets.UTF_8));
        commands.execute(CommandInput.builder()
                .line("echo ")
                .addArgs(String.format("'%s'",new String(bytes, StandardCharsets.UTF_8)), " > ", filePath.toString())
                .build());
    }
}
