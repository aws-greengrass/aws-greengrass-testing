/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.AbstractPlatform;
import com.aws.greengrass.testing.platform.NetworkUtils;

public class LinuxPlatform extends AbstractPlatform {

    private final NetworkUtilsLinux networkUtilsLinux;

    public LinuxPlatform(final Device device, final PillboxContext pillboxContext) {
        super(device, pillboxContext);
        networkUtilsLinux = new NetworkUtilsLinux();
    }

    @Override
    public LinuxCommands commands() {
        return new LinuxCommands(device, pillboxContext);
    }

    @Override
    public NetworkUtils getNetworkUtils() {
        return  this.networkUtilsLinux;
    }
}
