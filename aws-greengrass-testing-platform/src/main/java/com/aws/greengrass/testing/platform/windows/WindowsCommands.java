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
        throw new UnsupportedOperationException("Windows does not support this operation");
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        List<Integer> pidList = new ArrayList<>();
        pidList.add(pid);
        return pidList;
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        // Command to kill the Greengrass process
        execute(CommandInput.builder()
                .line("taskkill /F /PID " + processIds.stream()
                        .map(i -> Integer.toString(i))
                        .collect(Collectors.joining(" ")))
                .build());
        // Command to remove it as System service
        execute(CommandInput.of("sc delete greengrass"));
    }

    @Override
    public void makeExecutable(Path file) throws CommandExecutionException {
        throw new UnsupportedOperationException("Windows does not support this operation");
    }

    @Override
    public void installNucleus(CommandInput input, String user) throws CommandExecutionException {
        addUser(user);
        /* In Windows Greengrass needs to be setup as System service
           to let it deploy components on device with user other than default user */
        execute(CommandInput
                .builder()
                .from(input)
                .addArgs("--start", "true",
                        "--setup-system-service", "true",
                        "--component-default-user", user)
                .build());
    }

    @Override
    public int startNucleus(Path path, CommandInput input) throws CommandExecutionException {
        return greengrassPID();
    }

    private void addUser(String user) throws CommandExecutionException {
        // For Windows Greengrass doesn't support creation of user
        if (!executeToString(CommandInput.of("net user " + user)).contains(user)) {
            execute(CommandInput.of("net user /add " + user + " Greengrass@123"));
            execute(CommandInput.of("psexec -s cmd /c cmdkey /generic:" + user
                    + " /user:" + user + " /pass:Greengrass@123"));
        }
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
