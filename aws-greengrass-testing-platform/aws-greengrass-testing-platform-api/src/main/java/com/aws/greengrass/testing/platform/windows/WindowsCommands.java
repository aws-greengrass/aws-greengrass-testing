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
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

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
        throw new UnsupportedOperationException("No Windows yet");
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        throw new UnsupportedOperationException("No Windows yet");
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        throw new UnsupportedOperationException("No Windows yet");
    }

    @Override
    public void makeExecutable(Path file) throws CommandExecutionException {
        throw new UnsupportedOperationException("No Windows yet");
    }
}
