/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import java.io.IOException;

public abstract class NetworkUtils {
    protected static final String[] MQTT_PORTS = {"8883"};
    protected static final String[] NETWORK_PORTS = {"443"};

    /**
     * Disables incoming and outgoing MQTT connections by apply firewall rules.
     *
     * @throws InterruptedException then thread has been interrupted
     * @throws IOException on errors
     */
    public abstract void disconnectMqtt() throws InterruptedException, IOException;

    /**
     * Enables incoming and outgoing MQTT connections.
     *
     * @throws InterruptedException then thread has been interrupted
     * @throws IOException on errors
     */
    public abstract void recoverMqtt() throws InterruptedException, IOException;

    public abstract void addLoopbackAddress(String address) throws IOException, InterruptedException;

    public abstract void deleteLoopbackAddress(String address) throws IOException, InterruptedException;

    public abstract void disconnectNetwork() throws InterruptedException, IOException;

    public abstract void recoverNetwork() throws InterruptedException, IOException;
}
