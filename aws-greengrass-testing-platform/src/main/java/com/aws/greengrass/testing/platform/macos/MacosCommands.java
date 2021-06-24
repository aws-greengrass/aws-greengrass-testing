/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.UnixCommands;

public class MacosCommands extends UnixCommands {
    MacosCommands(final Device device) {
        super(device);
    }
}
