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

    void sigterm(List<Integer> processIds) throws CommandExecutionException;

    default void sigtermAll(int pid) throws CommandExecutionException {
        sigterm(findDescendants(pid));
    }

    void installNucleus(NucleusInstallationParameters installationParameters) throws CommandExecutionException;

    int startNucleus(Path rootDirectory) throws CommandExecutionException;

    void startGreengrassService() throws CommandExecutionException;

    void stopGreengrassService() throws CommandExecutionException;
}
