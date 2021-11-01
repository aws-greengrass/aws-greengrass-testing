/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "cat",
        description = "Reads a file from the local filesystem.")
public class Cat implements Callable<Integer> {
    private static final int BUFFER = 8192;

    @CommandLine.Parameters(index = "0")
    private String file;

    @Override
    public Integer call() throws Exception {
        Path filePath = Paths.get(file);
        int exitCode = Exists.call(filePath);
        if (exitCode > 0) {
            return exitCode;
        }
        if (!Files.isRegularFile(filePath)) {
            System.err.println("File '" + filePath + "' is not a file.");
            return 1;
        }
        final byte[] buffer = new byte[BUFFER];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            int read = input.read(buffer);
            while (read > 0) {
                System.out.write(buffer, 0, read);
                System.out.flush();
                read = input.read(buffer);
            }
        }
        return 0;
    }
}
