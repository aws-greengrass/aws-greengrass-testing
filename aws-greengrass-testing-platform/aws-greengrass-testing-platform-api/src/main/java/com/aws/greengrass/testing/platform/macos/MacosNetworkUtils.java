/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.platform.NetworkUtils;

import java.io.IOException;

public class MacosNetworkUtils extends NetworkUtils {
    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        throw new UnsupportedOperationException("Operation not supported");
    }
}
