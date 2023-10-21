/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.NetworkUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LinuxNetworkUtils extends NetworkUtils {
    private static final Logger LOGGER = LogManager.getLogger(LinuxNetworkUtils.class);
    private static final long TIMEOUT_IN_SECONDS = 2L;
    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLES = "iptables";
    private static final String IP = "ip";
    private static final String[] TEMPLATES = {
        "INPUT -p tcp -s localhost --dport %s -j ACCEPT",
        "INPUT -p tcp --dport %s -j DROP",
        "OUTPUT -p tcp -d localhost --dport %s -j ACCEPT",
        "OUTPUT -p tcp --dport %s -j DROP"
    };
    private static final String ADD_LOOPBACK_ADDRESS_TEMPLATE = "addr add %s/32 dev lo";
    private static final String DELETE_LOOPBACK_ADDRESS_TEMPLATE = "addr delete %s/32 dev lo";
    private final LinuxCommands commands;

    LinuxNetworkUtils(final Device device, final PillboxContext pillboxContext) {
        this.commands = new LinuxCommands(device, pillboxContext);
    }

    /**
     * Disables incoming and outgoing MQTT connections.
     * On Linux connections to/from 127.0.0.1 will still be allowed.
     */
    @Override
    public void disconnectMqtt() {
        modifyConnection(APPEND_OPTION, MQTT_PORTS);
    }

    @Override
    public void recoverMqtt() {
        modifyConnection(DISABLE_OPTION, MQTT_PORTS);
    }

    @Override
    public void addLoopbackAddress(String address) {
        String cmd = String.format(ADD_LOOPBACK_ADDRESS_TEMPLATE, address);

        CommandInput commandInput = CommandInput.builder()
                .line(IP)
                .addArgs(cmd)
                .timeout(TIMEOUT_IN_SECONDS)
                .build();

        commands.execute(commandInput);
    }

    @Override
    public void deleteLoopbackAddress(String address) {
        String cmd = String.format(DELETE_LOOPBACK_ADDRESS_TEMPLATE, address);
        CommandInput commandInput = CommandInput.builder()
                .line(IP)
                .addArgs(cmd)
                .timeout(TIMEOUT_IN_SECONDS)
                .build();

        commands.execute(commandInput);
    }

    private void modifyConnection(String action, String[] ports) {
        for (String template : TEMPLATES) {
            for (String port : ports) {
                List<String> arguments = new ArrayList<>();
                arguments.add(action);
                String cmd = String.format(template, port);
                LOGGER.debug("Running {} command: {}", IPTABLES, cmd);
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

    @Override
    public void disconnectNetwork() {
        modifyConnection(APPEND_OPTION, NETWORK_PORTS);
    }

    @Override
    public void recoverNetwork() {
        modifyConnection(DISABLE_OPTION, NETWORK_PORTS);
    }
}
