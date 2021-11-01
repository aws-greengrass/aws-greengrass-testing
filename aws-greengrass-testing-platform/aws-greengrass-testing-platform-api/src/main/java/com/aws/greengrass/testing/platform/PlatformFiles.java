/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface PlatformFiles {
    byte[] readBytes(Path filePath) throws CommandExecutionException;

    default String readString(Path filePath) throws CommandExecutionException {
        return new String(readBytes(filePath), StandardCharsets.UTF_8);
    }

    void delete(Path filePath) throws  CommandExecutionException;

    void makeDirectories(Path filePath) throws CommandExecutionException;

    List<Path> listContents(Path filePath) throws CommandExecutionException;

    void copyTo(Path source, Path destination) throws CopyException;

    boolean exists(Path filePath) throws CommandExecutionException;

    String format(Path filePath);
}
