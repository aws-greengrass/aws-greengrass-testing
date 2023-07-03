
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.platform.NetworkUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class MacosNetworkUtils extends NetworkUtils {
    private static final long TIMEOUT_IN_SECONDS = 2L;

    private static final String COMMAND_FORMAT = "ifconfig en0 %s";
    private static final String DOWN_OPERATION = "down";
    private static final String UP_OPERATION = "up";
    private static final AtomicBoolean mqttDown = new AtomicBoolean(false);
    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

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
        log.info("Running command: " + command);

        CommandInput command = CommandInput.builder()
                                .line("sh")
                                .addArgs("-c")
                                .addArgs(command)
                                .timeout(TIMEOUT_IN_SECONDS)
                                .build();
        try {
            commands.executeToString(command);
            log.info("Command {} result: ", command, result);
        } catch (CommandExecutionException e) {
                final String errorString = String.format(COMMAND_FAILED_TO_RUN, command);
                log.error(errorString, e);
                throw new RuntimeException(errorString, e);
        }
    }
}
