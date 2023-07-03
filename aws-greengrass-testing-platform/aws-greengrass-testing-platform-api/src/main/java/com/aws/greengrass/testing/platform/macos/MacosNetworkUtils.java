/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.NetworkUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MacosNetworkUtils extends NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger(MacosNetworkUtils.class);
    private static final long TIMEOUT_IN_SECONDS = 2L;

    private static final String COMMAND_FORMAT = "ifconfig en0 %s";
    private static final String DOWN_OPERATION = "down";
    private static final String UP_OPERATION = "up";
    private static final AtomicBoolean mqttDown = new AtomicBoolean(false);
    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

    private final MacosCommands commands;

    MacosNetworkUtils(final Device device, final PillboxContext pillboxContext) {
        super();
        this.commands = new MacosCommands(device, pillboxContext);
    }

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        String command = String.format(COMMAND_FORMAT, DOWN_OPERATION);
        collectProcessOutput(command);
        mqttDown.set(true);
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        if (mqttDown.get()) {
            String command = String.format(COMMAND_FORMAT, UP_OPERATION);
            collectProcessOutput(command);
            mqttDown.set(false);
        }
    }

    private void collectProcessOutput(String command) throws InterruptedException, IOException {
        LOGGER.info("Running command: " + command);

        CommandInput commandInput = CommandInput.builder()
                                .line("sh")
                                .addArgs("-c")
                                .addArgs(command)
                                .timeout(TIMEOUT_IN_SECONDS)
                                .build();
        try {
            String result = commands.executeToString(commandInput);
            LOGGER.info("Command {} result: ", command, result);
        } catch (CommandExecutionException e) {
            final String errorString = String.format(COMMAND_FAILED_TO_RUN, command);
            LOGGER.error(errorString, e);
            throw new RuntimeException(errorString, e);
        }
    }
}
