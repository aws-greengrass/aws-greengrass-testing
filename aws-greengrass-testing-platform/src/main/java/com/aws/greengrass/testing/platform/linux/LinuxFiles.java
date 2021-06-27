/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.UnixFiles;

public class LinuxFiles extends UnixFiles {
    public LinuxFiles(final Commands commands, final Device device) {
        super(commands, device);
    }
}
