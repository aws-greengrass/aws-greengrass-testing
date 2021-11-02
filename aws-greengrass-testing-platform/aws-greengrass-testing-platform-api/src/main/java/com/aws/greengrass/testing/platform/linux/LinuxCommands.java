/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.UnixCommands;

public class LinuxCommands extends UnixCommands {
    public LinuxCommands(final Device device, final PillboxContext pillboxContext) {
        super(device, pillboxContext);
    }
}
