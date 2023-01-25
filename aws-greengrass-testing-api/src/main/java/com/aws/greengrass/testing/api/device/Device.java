/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device;


import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public interface Device extends Closeable {
    String id();

    String type();

    PlatformOS platform();

    byte[] execute(CommandInput input, String test) throws CommandExecutionException;

    default String executeToString(CommandInput input) throws CommandExecutionException {
        return new String(execute(input, "test"), StandardCharsets.UTF_8);
    }

    boolean exists(String path);

    void copyTo(String source, String destination) throws CopyException;

    /**
     * Implementations of {@link Device} can override this method to release any resources
     * created by activating the device for testing.
     *
     * @throws IOException thrown if there was a problem cleaning up resources by activating the device
     */
    @Override
    default void close() throws IOException {
        // No-op
    }
}
