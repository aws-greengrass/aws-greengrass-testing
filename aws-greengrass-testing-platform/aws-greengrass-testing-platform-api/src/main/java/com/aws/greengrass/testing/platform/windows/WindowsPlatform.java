/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.AbstractPlatform;
import com.aws.greengrass.testing.platform.NetworkUtils;

public class WindowsPlatform extends AbstractPlatform {

    public WindowsPlatform(final Device device, final PillboxContext pillboxContext) {
        super(device, pillboxContext);
    }

    @Override
    public WindowsCommands commands() {
        return new WindowsCommands(device);
    }

    @Override
    public NetworkUtils networkUtils() {
        return new WindowsNetworkUtils(device);
    }
}
