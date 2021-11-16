/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface Commands {
    byte[] execute(CommandInput input) throws CommandExecutionException;

    default String executeToString(CommandInput input) throws CommandExecutionException {
        return new String(execute(input), StandardCharsets.UTF_8);
    }

    List<Integer> findDescendants(int pid) throws CommandExecutionException;

    void kill(List<Integer> processIds) throws CommandExecutionException;

    default void killAll(int pid) throws CommandExecutionException {
        kill(findDescendants(pid));
    }

    void installNucleus(Path rootDirectory, Map<String, String> args) throws CommandExecutionException;

    int startNucleus(Path rootDirectory) throws CommandExecutionException;

    String escapeSpaces(String input);
}
