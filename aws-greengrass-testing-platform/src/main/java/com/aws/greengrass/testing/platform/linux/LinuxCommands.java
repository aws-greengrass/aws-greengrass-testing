/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.UnixCommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LinuxCommands extends UnixCommands {
    private static final Pattern PID_REGEX = Pattern.compile("\\((\\d+)\\)");

    public LinuxCommands(final Device device) {
        super(device);
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        final String result = executeToString(CommandInput.builder()
                .line("ls /proc/" + pid + "/task")
                .build());
        return Arrays.stream(result.split("\\s")).map(String::trim).flatMap(line -> {
            final Matcher matcher = PID_REGEX.matcher(line);
            final List<Integer> pids = new ArrayList<>();
            while (matcher.find()) {
                pids.add(Integer.parseInt(matcher.group(1).trim()));
            }
            return pids.stream();
        }).collect(Collectors.toList());
    }
}
