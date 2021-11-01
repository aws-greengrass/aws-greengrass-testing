/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;

public abstract class AbstractPlatform implements Platform {
    protected final Device device;
    protected final PillboxContext pillboxContext;

    public AbstractPlatform(final Device device, final PillboxContext pillboxContext) {
        this.device = device;
        this.pillboxContext = pillboxContext;
    }

    @Override
    public PlatformFiles files() {
        return DevicePredicatePlatformFiles.localOrRemote(device, new RemoteFiles(device, pillboxContext));
    }
}
