/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.UnixCommands;

import java.util.regex.Pattern;

public class LinuxCommands extends UnixCommands {
    private static final Pattern PID_REGEX = Pattern.compile("\\((\\d+)\\)");

    public LinuxCommands(final Device device) {
        super(device);
    }
}
