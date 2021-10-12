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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WindowsFiles implements PlatformFiles {
    private static final Logger LOGGER = LogManager.getLogger(WindowsFiles.class);
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
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("read: " + filePath));
        }
    }

    @Override
    public String readString(Path filePath) throws CommandExecutionException {
        return PlatformFiles.super.readString(filePath);
    }

    @Override
    public void writeBytes(Path filePath, byte[] bytes) {
        try {
            Files.write(filePath, bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        try {
            Files.delete(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("read: " + filePath));
        }
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        try {
            Files.createDirectory(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        List<Path> contents = new ArrayList<>();
        try {
            contents = Files.list(filePath).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contents;
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        System.out.println("copy files in Windows");
        device.copyTo(source.toString(), destination.toString());
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        File file = new File(filePath.toString());
        return file.exists();
    }

    @Override
    public String format(Path filePath) {
        return filePath.toString().replaceAll("^[A-Za-z]:", "").replace("\\", "/");
    }
}
