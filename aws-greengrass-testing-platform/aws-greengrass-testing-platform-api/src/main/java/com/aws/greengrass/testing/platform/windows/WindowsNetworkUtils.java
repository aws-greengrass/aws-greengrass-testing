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
import java.util.HashMap;
import java.util.Map;

public class WindowsNetworkUtils extends NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger(WindowsNetworkUtils.class);
    private static final long TIMEOUT_IN_SECONDS = 10L;
    private static final String NETSH = "netsh";


    private static final Map<String, String> DIRECTIONS = new HashMap<String, String>() {{
        put("in", "localport");
        put("out", "remoteport");
    }};


    private static final String NETSH_ADD_RULE_FORMAT_STR
        = "advfirewall firewall add rule name='%s' protocol=tcp dir=%s action=block %s=%s";

    private static final String NETSH_ADD_LOOPBACK_ADDRESS = "interface ipv4 add address loopback %s mask=255.0.0.0";
    private static final String NETSH_DELETE_LOOPBACK_ADDRESS = "interface ipv4 delete address loopback %s";

    private static final String NETSH_DELETE_RULE_FORMAT_STR
        = "advfirewall firewall delete rule name='%s'";

    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

    // Windows requires a name for every firewall name (CAN'T have duplicates !!!)
    // Format: otf_uat_{PORT_NUMBER}_{DIRECTION}
    // Example:
    // otf_uat_8883_in
    private static final String FIREWALL_RULE_NAME_FORMAT = "otf_uat_%s_%s";

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

    @Override
    public void addLoopbackAddress(String address) throws IOException, InterruptedException {
        String command = String.format(NETSH_ADD_LOOPBACK_ADDRESS, address);
        runNetshCommand(command, false);
    }

    @Override
    public void deleteLoopbackAddress(String address) throws IOException, InterruptedException {
        String command = String.format(NETSH_DELETE_LOOPBACK_ADDRESS, address);
        runNetshCommand(command, false);
    }

    private String getRuleName(String port, String direction) {
        return String.format(FIREWALL_RULE_NAME_FORMAT, port, direction);
    }

    private void deleteRules(String... ports) throws InterruptedException, IOException {
        for (String port : ports) {
            for (String direction : DIRECTIONS.keySet()) {
                String ruleName = getRuleName(port, direction);
                String command = String.format(NETSH_DELETE_RULE_FORMAT_STR, ruleName);

                runNetshCommand(command, true);
            }
        }
    }

    private void blockPorts(String... ports) throws InterruptedException, IOException {
        for (String port : ports) {
            for (Map.Entry<String, String> entry : DIRECTIONS.entrySet()) {
                String direction = entry.getKey();
                String portDirection = entry.getValue();

                String ruleName = getRuleName(port, direction);
                String command = String.format(NETSH_ADD_RULE_FORMAT_STR, ruleName, direction, portDirection, port);

                runNetshCommand(command, false);
            }
        }
    }

    private void runNetshCommand(String command, boolean ignoreError) {
        LOGGER.info("Running {} command: {}", NETSH, command);

        CommandInput commandInput = CommandInput.builder()
                                .line(NETSH)
                                .addArgs(command.split(" "))
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

    @Override
    public void disconnectNetwork() {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void recoverNetwork() {
        throw new UnsupportedOperationException("Operation not supported");
    }
}
