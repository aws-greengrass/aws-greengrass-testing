/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public abstract class NetworkUtils {
    protected static final String[] MQTT_PORTS = {"8883", "443"};
    // 8888 and 8889 are used by the squid proxy which runs on a remote DUT
    // and need to disable access to test offline proxy scenarios
    protected static final String[] NETWORK_PORTS = {"443", "8888", "8889"};
    protected static final int[] GG_UPSTREAM_PORTS = {8883, 8443, 443};
    protected static final int SSH_PORT = 22;
    protected final List<Integer> blockedPorts = new ArrayList<>();

    public abstract void disconnectNetwork() throws InterruptedException, IOException;

    public abstract void recoverNetwork() throws InterruptedException, IOException;

}
