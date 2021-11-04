/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalFiles implements PlatformFiles {
    private final Device localDevice;

    public LocalFiles(final Device localDevice) {
        this.localDevice = localDevice;
    }

    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        System.out.println("Reading bytes in local files from " + filePath.toString());
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("read: " + filePath));
        }
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        try {
            FileUtils.recursivelyDelete(filePath);
        } catch (IOException ie) {
            throw new CommandExecutionException(ie, CommandInput.of("delete: " + filePath));
        }
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        try {
            Files.createDirectories(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("makeDirectories: " + filePath));
        }
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        return localDevice.exists(filePath.toString());
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        localDevice.copyTo(source.toString(), destination.toString());
    }

    @Override
    public String format(Path filePath) {
        return filePath.toString();
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        if (Files.isRegularFile(filePath)) {
            return Arrays.asList(filePath);
        }
        try (Stream<Path> files = Files.walk(filePath)) {
            return files.sorted(Comparator.reverseOrder())
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("listContents: " + filePath));
        }
    }

    @Override
    public void writeBytes(Path filePath, byte[] bytes) {
        System.out.println("Writing bytes in local files from " + filePath.toString());
        try {
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            Files.write(filePath, bytes);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("write bytes: " + filePath));
        }
    }
}
