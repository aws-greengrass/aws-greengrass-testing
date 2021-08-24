/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class UnixCommands implements Commands, UnixPathsMixin {
    private static final Logger LOGGER = LogManager.getLogger(UnixCommands.class);
    private static final Pattern PID_REGEX = Pattern.compile("\\((\\d+)\\)");
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
        execute(CommandInput.of("chmod +x " + file));
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        return findDirectDecendants().get(pid);
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        execute(CommandInput.builder()
                .line("kill " + processIds.stream()
                        .map(i -> Integer.toString(i))
                        .collect(Collectors.joining(" ")))
                .build());
    }

    protected Map<Integer, List<Integer>> findDirectDecendants() throws CommandExecutionException {
        //CommandInput.builder().line("sudo script -c \"ls /proc\" processes_info.txt").build();
        final String ppids = executeToString(CommandInput.builder().line("ls /proc").build());
        LOGGER.info("parent pids: " + ppids);
        List<Integer> parentPids = Arrays.stream(ppids.split("\\r?\\n")).map(String::trim).flatMap(line -> {
            final Matcher matcher = PID_REGEX.matcher(line);
            final List<Integer> pids = new ArrayList<>();
            while (matcher.find()) {
                pids.add(Integer.parseInt(matcher.group(1).trim()));
            }
            return pids.stream();
        }).collect(Collectors.toList());
        final Map<Integer, List<Integer>> pidMapping = new HashMap<>();
        for (int pid: parentPids) {
            final String children = executeToString(CommandInput.builder().line("ls /proc/" + pid + "/task").build());
            List<Integer> childPids = Arrays.stream(children.split("\\r?\\n")).map(String::trim).flatMap(line -> {
                final Matcher matcher = PID_REGEX.matcher(line);
                final List<Integer> pids = new ArrayList<>();
                while (matcher.find()) {
                    pids.add(Integer.parseInt(matcher.group(1).trim()));
                }
                return pids.stream();
            }).collect(Collectors.toList());
            if (!pidMapping.containsKey(pid)) {
                pidMapping.put(pid, childPids);
            } else {
                //check if value against pid is same as childPids. If not then update the value and store in the map.
            }
        }
        return pidMapping;
    }
}
