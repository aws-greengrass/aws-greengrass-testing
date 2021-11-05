/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.DevicePredicatePlatformFiles;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.google.auto.service.AutoService;

@AutoService(Platform.class)
public class WindowsPlatform implements Platform {
    private final Device device;

    public WindowsPlatform(final Device device) {
        this.device = device;
    }

    @Override
    public WindowsCommands commands() {
        return new WindowsCommands(device);
    }

    @Override
    public PlatformFiles files() {
<<<<<<< HEAD:aws-greengrass-testing-platform/aws-greengrass-testing-platform-api/src/main/java/com/aws/greengrass/testing/platform/windows/WindowsPlatform.java
        // throw new UnsupportedOperationException("No Windows yet!");
=======
>>>>>>> 410f0fd5871bd25cd7545f056fe48bcced6b77c9:aws-greengrass-testing-platform/src/main/java/com/aws/greengrass/testing/platform/windows/WindowsPlatform.java
        return DevicePredicatePlatformFiles.localOrRemote(device, new WindowsFiles(commands(), device));
    }
}
