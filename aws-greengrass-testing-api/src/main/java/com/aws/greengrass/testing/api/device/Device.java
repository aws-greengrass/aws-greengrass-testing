/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device;


import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.local.LocalDevice;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public interface Device {
    String id();

    String type();

    PlatformOS platform();

    byte[] execute(CommandInput input) throws CommandExecutionException;

    default String executeToString(CommandInput input) throws CommandExecutionException {
        return new String(execute(input), StandardCharsets.UTF_8);
    }

    void copyTo(Path source, Path destination) throws CopyException;

    default void sync(Path source) throws CopyException {
        copyTo(source, source);
    }

    boolean exists(Path file);

}
