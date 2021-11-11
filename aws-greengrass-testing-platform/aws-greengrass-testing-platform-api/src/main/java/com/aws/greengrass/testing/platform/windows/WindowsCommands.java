/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class WindowsCommands implements Commands {
    private final Device device;
    private static final long TIMEOUT_IN_SECONDS = 30L;

    WindowsCommands(final Device device) {
        this.device = device;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        final StringJoiner joiner = new StringJoiner(" ").add(input.line());
        Optional.ofNullable(input.args()).ifPresent(args -> args.forEach(joiner::add));
        return device.execute(CommandInput.builder()
                .line("cmd.exe /c")
                .addArgs(joiner.toString())
                .input(input.input())
                .timeout(input.timeout())
                .build());
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        List<Integer> pidList = new ArrayList<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(pid);
        while (!queue.isEmpty()) {
            pid = queue.poll();
            pidList.add(pid);
            String childPids = executeToString(
                    CommandInput.of("wmic process where (ParentProcessId=" + pid + ") get ProcessId"));
            String[] cids = childPids.split("\\r?\\n");
            if (cids != null && cids.length > 0) {
                for (String cid : cids) {
                    if (cid.trim().matches("\\d+")) {
                        queue.add(Integer.valueOf(cid.trim()));
                    }
                }
            }
        }
        return pidList;
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        // Command to kill the Greengrass process
        execute(CommandInput.builder()
                .line("taskkill /F /PID " + processIds.stream()
                        .map(i -> Integer.toString(i))
                        .collect(Collectors.joining(" /PID ")))
                .build());
        // Command to remove it as System service
        execute(CommandInput.of("sc delete greengrass"));
    }

    @Override
    public void installNucleus(Path rootDirectory, Map<String, String> args) throws CommandExecutionException {
        /* In Windows Greengrass needs to be setup as System service
           to let it deploy components on device with user other than default user */
        execute(CommandInput.builder()
                .line("java")
                .addArgs("-Droot=" + rootDirectory,
                        "-Dlog.store=" + args.get("-Dlog.store="),
                        "-Dlog.level=" + args.get("-Dlog.level="),
                        "-jar " + rootDirectory.resolve("greengrass/lib/Greengrass.jar").toString(),
                        "--aws-region " + args.get("--aws-region"),
                        "--env-stage " + args.get("--env-stage"),
                        "--start", "true",
                        "--setup-system-service", "true",
                        "--component-default-user", args.get("--component-default-user"))
                .timeout(TIMEOUT_IN_SECONDS)
                .build());
    }

    @Override
    public int startNucleus(Path rootDirectory) throws CommandExecutionException {
        return greengrassPID();
    }

    private int greengrassPID() throws CommandExecutionException {
        // This command is simply getting the pid of Greengrass process.
        String pid = executeToString(CommandInput.of("tasklist /FO csv /FI \"Imagename eq greengrass.exe\""));
        /* OUTPUT:
         * "Image Name","PID","Session Name","Session#","Mem Usage"
         * "<Process Name>","<PID>","<Session Name>","<Session Number>","<Memory Usage>"
         */
        List<String> processes = Arrays.stream(pid.split("\\r?\\n")).map(String::trim)
                .collect(Collectors.toList());
        //removing quotes around the pid
        return Integer.parseInt(processes
                .get(1)
                .split(",") [1]
                .replace("\"", ""));
    }
}
