/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.remote;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public abstract class AbstractRemoteDevice implements Device {
    private static final Logger LOGGER = LogManager.getLogger(AbstractRemoteDevice.class);
    protected final PillboxContext pillboxContext;

    public AbstractRemoteDevice(final PillboxContext pillboxContext) {
        this.pillboxContext = pillboxContext;
    }

    @Override
    public boolean exists(final String path) {
        try {
            execute(CommandInput.builder()
                    .line("java")
                    .addArgs("-jar", pillboxContext.onDevice().toString(),
                            "files", "exists", path)
                    .build());  
            return true;
        } catch (CommandExecutionException e) {
            LOGGER.debug("Failed to check if path {} exists, assuming false", path, e);
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        // Eject the binary on the device upon closure
        execute(CommandInput.builder()
                .line("java")
                .addArgs("-jar", pillboxContext.onDevice().toString(),
                        "files", "rm", pillboxContext.onDevice().toString())
                .build());
    }
}
