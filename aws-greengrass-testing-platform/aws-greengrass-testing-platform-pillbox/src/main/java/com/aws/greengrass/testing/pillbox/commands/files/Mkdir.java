/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "mkdir",
        description = "Create directories on the file system"
)
public class Mkdir implements Callable<Integer> {
    @CommandLine.Option(names = "-p", description = "Create directories recursively")
    private boolean recursive;

    @CommandLine.Parameters(index = "0")
    private String file;

    @Override
    public Integer call() throws Exception {
        final Path filePath = Paths.get(file);
        int exitCode = 0;
        if (recursive) {
            Files.createDirectories(filePath);
        } else {
            Path parentPath = filePath.getParent();
            if (Objects.nonNull(parentPath) && (exitCode = Exists.call(parentPath)) != 0) {
                return exitCode;
            }
            Files.createDirectory(filePath);
        }
        return exitCode;
    }
}
