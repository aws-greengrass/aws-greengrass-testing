
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.platform.NetworkUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LinuxNetworkUtils extends NetworkUtils {
    private static final long TIMEOUT_IN_SECONDS = 2L;

    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR
            = "iptables %s INPUT -p tcp -s localhost --dport %s -j ACCEPT && "
            + "iptables %s INPUT -p tcp --dport %s -j DROP && "
            + "iptables %s OUTPUT -p tcp -d localhost --dport %s -j ACCEPT && "
            + "iptables %s OUTPUT -p tcp --dport %s -j DROP";
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
        for (String port : MQTT_PORTS) {
            CommandInput command = CommandInput.builder()
                                .line("sh")
                                .addArgs("-c")
                                .addArgs(String.format(IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR,
                                                        action, port, action, port, action, port, action, port))
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
}
