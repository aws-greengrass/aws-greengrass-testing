/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.PlatformFiles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WindowsFiles implements PlatformFiles {
    protected final Commands commands;
    private final Device device;
    private final PlatformOS host;

    /**
     * A windows based platform. All commands and paths are formatted appropriately.
     *
     * @param commands the {@link Commands} interface this platform will interact with
     * @param device the underlying {@link Device} this platform interacts with
     */
    public WindowsFiles(Commands commands, Device device) {
        this.host = PlatformOS.currentPlatform();
        this.device = device;
        this.commands = commands;
    }

    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        return commands.execute(CommandInput.of("more " + filePath.toString()));
    }

    @Override
    public void writeBytes(Path filePath, byte[] bytes) {
        commands.execute(CommandInput.of("echo " + bytes.toString() + " > " + filePath));
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        commands.execute(CommandInput.of("del " + filePath.toString()));
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        commands.execute(CommandInput.of("mkdir " + filePath.toString()));
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        final String[] files = commands.executeToString(CommandInput.of("dir /B /S " + filePath.toString()))
                .split("\\r?\\n");
        return Arrays.stream(files)
                .map(String::trim)
                .filter(file -> !file.isEmpty())
                .map(Paths::get)
                .collect(Collectors.toList());
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        device.copyTo(source.toString(), destination.toString());
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        return device.exists(format(filePath));
    }

    @Override
    public String format(Path filePath) {
        return filePath.toString().replaceAll("^[A-Za-z]:", "").replace("\\", "/");
    }
}
