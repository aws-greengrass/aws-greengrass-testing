/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.platform.NetworkUtils;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class NetworkUtilsLinux extends NetworkUtils {
    private static final String ENABLE_OPTION = "--insert";
    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLE_COMMAND_BLOCK_INGRESS_STR =
            "sudo iptables %s INPUT -p tcp --sport %s -j REJECT";
    private static final String IPTABLE_COMMAND_STR = "sudo iptables %s OUTPUT -p tcp --dport %s -j REJECT && "
            + "sudo iptables %s INPUT -p tcp --sport %s -j REJECT";
    private static final String IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR =
            "sudo iptables %s INPUT -p tcp -s localhost --dport %s -j ACCEPT && "
                    +
                    "sudo iptables %s INPUT -p tcp --dport %s -j DROP && "
                    +
                    "sudo iptables %s OUTPUT -p tcp -d localhost --dport %s -j ACCEPT && "
                    +
                    "sudo iptables %s OUTPUT -p tcp --dport %s -j DROP";
    private static final String IPTABLE_SAFELIST_COMMAND_STR
            = "sudo iptables %s OUTPUT -p tcp -d %s --dport %d -j ACCEPT && "
            +
            "sudo iptables %s INPUT -p tcp -s %s --sport %d -j ACCEPT";
    private static final String GET_IPTABLES_RULES = "sudo iptables -S";

    // The string we are looking for to verify that there is an iptables rule to reject a port
    // We only need to look for sport because sport only gets created if dport is successful
    private static final String IPTABLES_RULE = "-m tcp --sport %s -j REJECT";

    private static final AtomicBoolean bandwidthSetup = new AtomicBoolean(false);


    private void modifyMqttConnection(String action) throws IOException, InterruptedException {
        for (String port : MQTT_PORTS) {
            new ProcessBuilder().command(
                    "sh", "-c", String.format(IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR,
                            action, port, action, port, action, port, action, port)
            ).start().waitFor(2, TimeUnit.SECONDS);
        }
    }

    private void filterPortOnInterface(String iface, int port) throws IOException, InterruptedException {
        // Filtering SSH traffic impacts test execution, so we explicitly disallow it
        if (port == SSH_PORT) {
            return;
        }
        List<String> filterSourcePortCommand = Stream.of("sudo", "tc", "filter", "add", "dev",
                iface, "parent", "1:", "protocol", "ip", "prio", "1", "u32", "match",
                "ip", "sport", Integer.toString(port), "0xffff", "flowid", "1:2").collect(Collectors.toList());
        executeCommand(filterSourcePortCommand);

        List<String> filterDestPortCommand = Stream.of("sudo", "tc", "filter", "add", "dev", iface,
                "parent", "1:", "protocol", "ip", "prio", "1", "u32", "match",
                "ip", "dport", Integer.toString(port), "0xffff", "flowid", "1:2").collect(Collectors.toList());
        executeCommand(filterDestPortCommand);
    }

    private void deleteRootNetemQdiscOnInterface() throws InterruptedException, IOException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            if (netint.isPointToPoint() || netint.isLoopback()) {
                continue;
            }
            executeCommand(Stream.of("sudo", "tc", "qdisc", "del", "dev", netint.getName(), "root")
                            .collect(Collectors.toList()));
        }
    }

    private void createRootNetemQdiscOnInterface(String iface, int netemRateKbps)
            throws InterruptedException, IOException {
        // TODO: Add support for setting packet loss and delay
        int netemDelayMs = 750;
        List<String> addQdiscCommand = Stream.of("sudo", "tc", "qdisc", "add", "dev", iface, "root", "handle",
                "1:", "prio", "bands", "2", "priomap", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
                "0", "0", "0", "0", "0").collect(Collectors.toList());
        executeCommand(addQdiscCommand);

        List<String> netemCommand =
                Stream.of("sudo", "tc", "qdisc", "add", "dev", iface, "parent", "1:2", "netem", "delay",
                        String.format("%dms", netemDelayMs), "rate", String.format("%dkbit", netemRateKbps))
                .collect(Collectors.toList());
        executeCommand(netemCommand);
    }

    private String executeCommand(List<String> command) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder().command(command).start();
        proc.waitFor(2, TimeUnit.SECONDS);
        if (proc.exitValue() != 0) {
            throw new IOException("CLI command " + command + " failed with error "
                    + new String(IoUtils.toByteArray(proc.getErrorStream()), StandardCharsets.UTF_8));
        }
        return new String(IoUtils.toByteArray(proc.getInputStream()), StandardCharsets.UTF_8);
    }

    @Override
    public void disconnectNetwork() throws InterruptedException, IOException {
        interfacepolicy(IPTABLE_COMMAND_STR, ENABLE_OPTION, "connection-loss", NETWORK_PORTS);
    }

    @Override
    public void recoverNetwork() throws InterruptedException, IOException {
        interfacepolicy(IPTABLE_COMMAND_STR, DISABLE_OPTION, "connection-recover", NETWORK_PORTS);

        if (bandwidthSetup.get()) {
            deleteRootNetemQdiscOnInterface();
            bandwidthSetup.set(false);
        }
    }

    private void interfacepolicy(String iptableCommandString, String option, String eventName, String... ports)
            throws InterruptedException, IOException {
        for (String port : ports) {
            new ProcessBuilder().command("sh", "-c", String.format(iptableCommandString, option, port, option, port))
                    .start().waitFor(2, TimeUnit.SECONDS);
        }
    }
}
