/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class WindowsCommands implements Commands {
    private final Device device;

    WindowsCommands(final Device device) {
        this.device = device;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        final StringJoiner joiner = new StringJoiner(" ").add(input.line());
        Optional.ofNullable(input.args()).ifPresent(args -> args.forEach(joiner::add));
        return device.execute(CommandInput.builder()
                .line("cmd.exe")
                .addArgs("/c", joiner.toString())
                .input(input.input())
                .timeout(input.timeout())
                .build());
    }

    @Override
    public int executeInBackground(CommandInput input) throws CommandExecutionException {
        String pid = executeToString(CommandInput.of("tasklist /FO csv /FI \"Imagename eq greengrass.exe\""));
        List<String> processes = Arrays.stream(pid.split("\\r?\\n")).map(String::trim)
                .collect(Collectors.toList());
        return Integer.parseInt(processes.get(1).split(",") [1].replace("\"", ""));
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        List<Integer> pidList = new ArrayList<>();
        pidList.add(pid);
        return pidList;
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        execute(CommandInput.builder()
                .line("taskkill /F /PID " + processIds.stream()
                        .map(i -> Integer.toString(i))
                        .collect(Collectors.joining(" ")))
                .build());
        execute(CommandInput.of("sc delete greengrass"));
    }

    @Override
    public void makeExecutable(Path file) throws CommandExecutionException {
        // .cmd file is already executable
    }
}
