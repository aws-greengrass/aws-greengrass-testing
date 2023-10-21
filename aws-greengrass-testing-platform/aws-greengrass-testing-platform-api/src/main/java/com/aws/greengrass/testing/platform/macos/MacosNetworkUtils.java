/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.NetworkUtils;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MacosNetworkUtils extends NetworkUtils {
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(MacosNetworkUtils.class);
    private static final String NETWORK_SETUP_COMMAND = "networksetup";
    private static final String DOWN_OPERATION = "off";
    private static final String UP_OPERATION = "on";
    private static final String ACTIVE_SERVICE = "Active";
    private static final String INACTIVE_SERVICE = "Inactive";
    private static final long TIMEOUT_IN_SECONDS = 2L;
    private static final AtomicBoolean networkDown = new AtomicBoolean(false);
    private final MacosCommands commands;

    MacosNetworkUtils(final Device device, final PillboxContext pillboxContext) {
        this.commands = new MacosCommands(device, pillboxContext);
    }

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void addLoopbackAddress(String address) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void deleteLoopbackAddress(String address) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void disconnectNetwork() {
        List<String> activeNetworkServices = networkServicesList().get(ACTIVE_SERVICE);
        LOGGER.debug("Active network service {}", activeNetworkServices);
        if (!activeNetworkServices.isEmpty()) {
            for (String networkService : activeNetworkServices) {
                commands.execute(CommandInput.builder()
                        .line(NETWORK_SETUP_COMMAND)
                        .addArgs("-setnetworkserviceenabled", networkService, DOWN_OPERATION)
                        .timeout(TIMEOUT_IN_SECONDS)
                        .build());
            }
            networkDown.set(true);
        }
    }

    @Override
    public void recoverNetwork() {
        if (networkDown.get()) {
            List<String> inactiveNetworkServices = networkServicesList().get(INACTIVE_SERVICE);
            LOGGER.debug("Inactive network service {}", inactiveNetworkServices);
            if (!inactiveNetworkServices.isEmpty()) {
                for (String networkService : inactiveNetworkServices) {
                    commands.execute(CommandInput.builder()
                            .line(NETWORK_SETUP_COMMAND)
                            .addArgs("-setnetworkserviceenabled", networkService, UP_OPERATION)
                            .timeout(TIMEOUT_IN_SECONDS)
                            .build());
                }
            }
        }
    }

    /**
     * Gets list of all network services and creates a map with Active and Inactive status.
     * @return Map of list of active and inactive network services
     */
    private Map<String, List<String>> networkServicesList() {
        Map<String, List<String>> networkList = new HashMap<>();
        String response = commands.executeToString(CommandInput.builder()
                .line(NETWORK_SETUP_COMMAND)
                .addArgs("-listallnetworkservices")
                .build());
        String[] networkServiceList = response.split("\\r?\\n|\\r");
        for (int i = 1; i < networkServiceList.length; i++) {
            String netService = networkServiceList[i];
            if (netService.startsWith("*")) {
                netService = "\"".concat(netService.substring(1, netService.length())).concat("\"");
                networkList.computeIfAbsent(INACTIVE_SERVICE, k -> new ArrayList<>()).add(netService);
            } else {
                netService = "\"".concat(netService).concat("\"");
                networkList.computeIfAbsent(ACTIVE_SERVICE, k -> new ArrayList<>()).add(netService);
            }
        }
        return networkList;
    }
}
