/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class UnixCommands implements Commands, UnixPathsMixin {
    private static final Pattern PID_REGEX = Pattern.compile("^(\\d*)\\s*");
    protected final Device device;
    private final PlatformOS host;

    public UnixCommands(final Device device) {
        this.device = device;
        this.host = PlatformOS.currentPlatform();
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

    @Override
    public int executeInBackground(CommandInput input) throws CommandExecutionException {
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

    @Override
    public void makeExecutable(Path file) throws CommandExecutionException {
        execute(CommandInput.of("chmod +x " + file.toString()));
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        final String result = executeToString(CommandInput.builder()
                .line("pstree -p " + pid + " | grep -o '([0-9]\\+)' | grep -o '[0-9]\\+'")
                .build());
        return Arrays.stream(result.split("\\r?\\n")).map(String::trim).flatMap(line -> {
            final Matcher matcher = PID_REGEX.matcher(line);
            final List<Integer> pids = new ArrayList<>();
            while (matcher.find()) {
                pids.add(Integer.parseInt(matcher.group(1).trim()));
            }
            return pids.stream();
        }).collect(Collectors.toList());
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        execute(CommandInput.builder()
                .line("kill " + processIds.stream()
                        .map(i -> Integer.toString(i))
                        .collect(Collectors.joining(" ")))
                .build());
    }
}
