/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(
        name = "rm",
        description = "Remove files and directories.")
public class Remove implements Callable<Integer> {
    @CommandLine.Option(names = { "-r", "--recursive" }, description = "Remove all files and directories recursively")
    private boolean recursive;

    @CommandLine.Parameters(index = "0")
    private String file;

    @Override
    public Integer call() throws Exception {
        Path filePath = Paths.get(file);
        int exitCode = Exists.call(filePath);
        if (exitCode > 0) {
            return exitCode;
        }
        if (Files.isRegularFile(filePath)) {
            Files.delete(filePath);
            return 0;
        }
        if (recursive) {
            try (Stream<Path> files = Files.walk(filePath)) {
                files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } else {
            System.out.println("cannot remove '" + filePath + "': Is a directory");
        }
        return 0;
    }
}
