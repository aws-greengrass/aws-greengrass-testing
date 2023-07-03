/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.NetworkUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LinuxNetworkUtils extends NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger(LinuxNetworkUtils.class);
    private static final long TIMEOUT_IN_SECONDS = 2L;

    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLES = "iptables";
    private static final String[] TEMPLATES = {
        "INPUT -p tcp -s 127.0.0.1 --dport %s -j ACCEPT",
        "INPUT -p tcp --dport %s -j DROP",
        "OUTPUT -p tcp -d 127.0.0.1 --dport %s -j ACCEPT",
        "OUTPUT -p tcp --dport %s -j DROP"
    };

    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

    private final LinuxCommands commands;

    LinuxNetworkUtils(final Device device, final PillboxContext pillboxContext) {
        this.commands = new LinuxCommands(device, pillboxContext);
    }

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        modifyMqttConnection(APPEND_OPTION);
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        modifyMqttConnection(DISABLE_OPTION);
    }

    private void modifyMqttConnection(String action) throws IOException, InterruptedException {
        for (String template : TEMPLATES) {
            for (String port : MQTT_PORTS) {
                List<String> arguments = new ArrayList<>();
                arguments.add(action);
                String cmd = String.format(template, port);
                LOGGER.info("Running {} command: {}", IPTABLES, cmd);
                arguments.addAll(Arrays.asList(cmd.split(" ")));

                CommandInput commandInput = CommandInput.builder()
                                .line(IPTABLES)
                                .addArgs(arguments.toArray(new String[0]))
                                .timeout(TIMEOUT_IN_SECONDS)
                                .build();

                commands.execute(commandInput);
            }
        }
    }
}
