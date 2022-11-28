/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.NucleusInstallationParameters;
import com.google.common.annotations.VisibleForTesting;

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
    private static final String GG_START_ARGUMENT = "--start";
    private static final String TRUE = "true";
    private static final String JAVA = "java";
    private static final String JAR = "-jar";
    private static final String GG_JAR_PATH_RELATIVE_TO_ROOT = "greengrass/lib/Greengrass.jar";
    private static final String GG_SETUP_SYSTEM_SERVICE_PARAMETER = "--setup-system-service";


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
    public void installNucleus(NucleusInstallationParameters installationParameters) throws CommandExecutionException {
        List<String> arguments = new ArrayList<>();

        if (installationParameters.getJvmArguments() != null) {
            arguments.addAll(installationParameters.getJvmArguments());
        }

        if (installationParameters.getSystemProperties() != null) {
            installationParameters.getSystemProperties().forEach((k, v) -> {
                StringBuilder sb = new StringBuilder("-D");
                sb.append(k).append("=").append(v);
                arguments.add(sb.toString());
            });
        }

        arguments.add(JAR);
        arguments.add(installationParameters.getGreengrassRootDirectoryPath()
                .resolve(GG_JAR_PATH_RELATIVE_TO_ROOT).toString());

        if (installationParameters.getGreengrassParameters() != null) {
            installationParameters.getGreengrassParameters().forEach((k, v) -> {
                arguments.add(k);
                arguments.add(v);
            });
        }

        arguments.add(GG_START_ARGUMENT);
        arguments.add(TRUE);
        arguments.add(GG_SETUP_SYSTEM_SERVICE_PARAMETER);
        arguments.add(TRUE);

        execute(CommandInput.builder()
                .line(JAVA)
                .addArgs(arguments.toArray(new String[0]))
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

    @Override
    public void startGreengrassService() throws CommandExecutionException {
        return;
    }

    @Override
    public void stopGreengrassService() throws CommandExecutionException {
        return;
    }
}
