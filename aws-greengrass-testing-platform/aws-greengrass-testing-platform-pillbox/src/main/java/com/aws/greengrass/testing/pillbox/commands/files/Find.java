/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

@CommandLine.Command(
        name = "find",
        description = "List all files and directories.")
public class Find implements Callable<Integer> {
    enum Type implements Predicate<Path> {
        FILE("f"), DIRECTORY("d");

        String code;

        Type(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        @Override
        public boolean test(Path path) {
            switch (this) {
                case FILE:
                    return Files.isRegularFile(path);
                case DIRECTORY:
                    return Files.isDirectory(path);
                default:
                    return false;
            }
        }

        public static Type fromCode(String code) {
            for (Type type : values()) {
                if (type.code().equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Could not find a type for '" + code + "'");
        }
    }

    @CommandLine.Option(names = "-maxdepth", description = "Max recursion depth")
    private int maxdepth = -1;

    @CommandLine.Option(names = "-name", description = "Filter files by glob name, ie: *.txt")
    private String name = "*";

    @CommandLine.Option(names = "-type", description = "The file type to filter")
    private String[] type;

    @CommandLine.Parameters(index = "0")
    private String file;

    private Predicate<Path> generateTypePredicate() {
        return filePath -> {
            return Optional.ofNullable(type)
                    .map(t -> Arrays.stream(t).map(Type::fromCode))
                    .orElseGet(() -> Stream.of(Type.FILE, Type.DIRECTORY))
                    .anyMatch(p -> p.test(filePath));
        };
    }

    private void printFilePath(Path filePath, int currentDepth) throws IOException {
        PathMatcher matcher = filePath.getFileSystem().getPathMatcher("glob:" + name);
        Predicate<Path> isType = generateTypePredicate();
        if (isType.test(filePath) && matcher.matches(filePath.getFileName())) {
            System.out.println(filePath);
        }
        if (Files.isDirectory(filePath) && (maxdepth == -1 || currentDepth < maxdepth)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(filePath)) {
                for (Path nextFile : files) {
                    printFilePath(nextFile, currentDepth + 1);
                }
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        Path filePath = Paths.get(file);
        if (Files.notExists(filePath)) {
            System.out.println("File " + filePath + " does not exists");
            return 0;
        }
        printFilePath(filePath, 0);
        return 0;
    }
}
