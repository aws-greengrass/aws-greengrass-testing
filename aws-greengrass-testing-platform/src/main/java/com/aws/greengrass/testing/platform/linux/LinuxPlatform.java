/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.DevicePredicatePlatformFiles;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.google.auto.service.AutoService;

@AutoService(Platform.class)
public class LinuxPlatform implements Platform {
    private final Device device;

    public LinuxPlatform(final Device device) {
        this.device = device;
    }

    @Override
    public LinuxCommands commands() {
        return new LinuxCommands(device);
    }

    @Override
    public PlatformFiles files() {
        return DevicePredicatePlatformFiles.localOrRemote(device, new LinuxFiles(commands()));
    }
}
