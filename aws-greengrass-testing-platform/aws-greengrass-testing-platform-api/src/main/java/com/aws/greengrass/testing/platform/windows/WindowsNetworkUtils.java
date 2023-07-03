/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.NetworkUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class WindowsNetworkUtils extends NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger(WindowsNetworkUtils.class);
    private static final long TIMEOUT_IN_SECONDS = 10L;

    private static final String NETSH_ADD_RULE_FORMAT
            = "netsh advfirewall firewall add rule name='%s' protocol=tcp dir=in action=block localport=%s && "
            + "netsh advfirewall firewall add rule name='%s' protocol=tcp dir=out action=block remoteport=%s";
    private static final String NETSH_DELETE_RULE_FORMAT = "netsh advfirewall firewall delete rule name='%s'";
    private static final String NETSH_GET_RULE_FORMAT = "netsh advfirewall firewall show rule name='%s'";
    private static final String NO_RULE_FOUND_STRING = "No rules match the specified criteria.";
    private static final String ADD_LOOPBACK_ADDR_FORMAT
        = "netsh interface ipv4 add address LOOPBACK %s 255.255.255.255";
    private static final String REMOVE_LOOPBACK_ADDR_FORMAT
        = "netsh interface ipv4 delete address LOOPBACK %s 255.255.255.255";
    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

    // Windows requires a name for every firewall name (can have duplicates)
    // Format: otf_uat_{PORT_NUMBER}
    // Example:
    // otf_uat_8883
    private static final String FIREWALL_RULE_NAME_FORMAT = "otf_uat_%s";

    private final WindowsCommands commands;

    WindowsNetworkUtils(final Device device) {
        super();
        this.commands = new WindowsCommands(device);
    }

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        blockPorts(MQTT_PORTS);
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        deleteRules(MQTT_PORTS);
    }

    private String getRuleName(String port) {
        return String.format(FIREWALL_RULE_NAME_FORMAT, port);
    }

    private void deleteRules(String... ports) throws InterruptedException, IOException {
        for (String port : ports) {
            String ruleName = getRuleName(port);
            String command = String.format(NETSH_DELETE_RULE_FORMAT, ruleName);

            runCommandInTerminal(command, true);
        }
    }

    private void blockPorts(String... ports) throws InterruptedException, IOException {
        for (String port : ports) {
            String ruleName = getRuleName(port);
            // Create 2 rules (can have same name) one for in and one for out
            String command = String.format(NETSH_ADD_RULE_FORMAT,
                ruleName,
                port,
                ruleName,
                port);

            runCommandInTerminal(command, false);
        }
    }

    private void runCommandInTerminal(String command, boolean ignoreError) throws IOException, InterruptedException {
        LOGGER.info("Running command: " + command);
        CommandInput commandInput = CommandInput.builder()
                                .line("cmd")
                                .addArgs("/c")
                                .addArgs(command)
                                .timeout(TIMEOUT_IN_SECONDS)
                                .build();
        try {
            String result = commands.executeToString(commandInput);
            LOGGER.info("Command {} result: ", command, result);
        } catch (CommandExecutionException e) {
            final String errorString = String.format(COMMAND_FAILED_TO_RUN, command);
            LOGGER.error(errorString, e);
            if (!ignoreError) {
                throw new RuntimeException(errorString, e);
            }
        }
    }
}
