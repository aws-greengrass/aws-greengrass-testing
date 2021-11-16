/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.api.model.PillboxContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public abstract class UnixCommands implements Commands, UnixPathsMixin {
    private static final Logger LOGGER = LogManager.getLogger(UnixCommands.class);
    protected final Device device;
    protected final PillboxContext pillboxContext;
    private final PlatformOS host;
    private static final long TIMEOUT_IN_SECONDS = 30L;

    /**
     * Constructs a new Unix based platform support with a device abstraction and pillbox binary.
     *
     * @param device the underlying {@link Device} abstraction representing this {@link Platform}
     * @param pillboxContext the {@link PillboxContext} location for the pillbox binary
     */
    public UnixCommands(final Device device, final PillboxContext pillboxContext) {
        this.host = PlatformOS.currentPlatform();
        this.device = device;
        this.pillboxContext = pillboxContext;
    }

    @Override
    public PlatformOS host() {
        return host;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        final StringJoiner joiner = new StringJoiner(" ").add(input.line());
        Optional.ofNullable(input.args()).ifPresent(args -> args.forEach(joiner::add));
        return device.execute(CommandInput.builder()
                .workingDirectory(input.workingDirectory())
                .line("sh")
                .addArgs("-c", formatToUnixPath(joiner.toString()))
                .input(input.input())
                .timeout(input.timeout())
                .build());
    }

    private int executeInBackground(CommandInput input) throws CommandExecutionException {
        String output = "output.log";
        if (Objects.nonNull(input.workingDirectory())) {
            output = input.workingDirectory().resolve(output).toString();
        }
        byte[] rawBytes = execute(CommandInput.builder()
                .from(input)
                .addArgs("1> " + output + " 2>&1 & echo $!")
                .build());
        return Integer.parseInt(new String(rawBytes, StandardCharsets.UTF_8).trim());
    }

    private void makeExecutable(Path file) throws CommandExecutionException {
        execute(CommandInput.of("chmod +x " + file));
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        String pillboxPath = pillboxContext.onDevice().toString();
        // We'll use the pillbox even on the local device
        if (device.type().equals(LocalDevice.TYPE)) {
            pillboxPath = pillboxContext.onHost().toString();
        }
        final CommandInput in = CommandInput.builder()
                .line("java")
                .addArgs("-jar", pillboxPath)
                .addArgs("process", "descendants", Integer.toString(pid))
                .build();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(execute(in))))) {
            return reader.lines().map(Integer::parseInt).collect(Collectors.toList());
        } catch (IOException ie) {
            throw new CommandExecutionException(ie, in);
        }
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        execute(CommandInput.builder()
            .line("kill " + processIds.stream()
                    .map(i -> Integer.toString(i))
                    .collect(Collectors.joining(" ")))
            .build());
    }

    @Override
    public void installNucleus(Path rootDirectory, Map<String, String> args) throws CommandExecutionException {
        execute(CommandInput.builder()
                .line("java")
                .addArgs("-Droot=" + rootDirectory,
                        "-Dlog.store=" + args.get("-Dlog.store="),
                        "-Dlog.level=" + args.get("-Dlog.level="),
                        "-jar", rootDirectory.resolve("greengrass/lib/Greengrass.jar").toString(),
                        "--aws-region", args.get("--aws-region"),
                        "--env-stage", args.get("--env-stage"),
                        "--start", "false")
                .timeout(TIMEOUT_IN_SECONDS)
                .build());
    }

    @Override
    public int startNucleus(Path rootDirectory) throws CommandExecutionException {
        Path loaderPath = rootDirectory.resolve("alts/current/distro/bin/loader");
        makeExecutable(loaderPath);
        return executeInBackground(CommandInput.builder()
                .workingDirectory(rootDirectory)
                .line(loaderPath.toString())
                .timeout(TIMEOUT_IN_SECONDS)
                .build());
    }

    @Override
    public String escapeSpaces(String input) {
        return input.replaceAll(" ", "\\ ");
    }
}
