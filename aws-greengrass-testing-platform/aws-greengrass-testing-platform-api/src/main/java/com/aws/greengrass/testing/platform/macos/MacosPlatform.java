/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.AbstractPlatform;
import com.aws.greengrass.testing.platform.NetworkUtils;

public class MacosPlatform extends AbstractPlatform {

    public MacosPlatform(final Device device, final PillboxContext pillboxContext) {
        super(device, pillboxContext);
    }

    @Override
    public MacosCommands commands() {
        return new MacosCommands(device, pillboxContext);
    }

    @Override
    public NetworkUtils networkUtils() {
        return new MacosNetworkUtils(device, pillboxContext);
    }
}
