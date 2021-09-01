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
        Map<Integer, List<Integer>> pidMap = findDirectDescendants();
        List<Integer> child = new ArrayList<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(pid);
        while (!queue.isEmpty()) {
            int process = queue.poll();
            child.add(process);
            if (pidMap.get(process) != null) {
                pidMap.get(process).stream().forEach(processId -> queue.add(processId));
            }
        }
        return child;
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        execute(CommandInput.builder()
            .line("kill " + processIds.stream()
                    .map(i -> Integer.toString(i))
                    .collect(Collectors.joining(" ")))
            .build());
    }

    protected Map<Integer, List<Integer>> findDirectDescendants() throws CommandExecutionException {
        // TODO: replace all system FS commands with a platform independent solution
        Map<Integer, List<Integer>> pidMapping = new HashMap<>();
        // check if process has any child process running
        // Example Output: /proc/<childPID>/status:PPid: <parentPID>
        final String processIds = executeToString(CommandInput.builder()
                .line("find /proc/ -name 'status' -maxdepth 2 -exec grep PPid /dev/null 2>&1 {} \\; || true")
                .build());
        List<String> processes = Arrays.stream(processIds.split("\\r?\\n")).map(String::trim)
                .collect(Collectors.toList());
        for (String process : processes) {
            if (process.contains("No such file or directory")) {
                LOGGER.info("Process {} no longer exists", process.substring(13, 18));
                continue;
            }
            String[] ppid = process.split("\\s");
            int childId = Integer.parseInt(ppid[0].split("/")[2]);
            int parentId = Integer.parseInt(ppid[1]);
            List<Integer> childPids = new ArrayList<>();
            childPids.add(childId);
            if (pidMapping.containsKey(parentId)) {
                childPids.addAll(pidMapping.get(parentId));
            }
            pidMapping.put(Integer.parseInt(ppid[1]), childPids);
        }
        return pidMapping;
    }
}
