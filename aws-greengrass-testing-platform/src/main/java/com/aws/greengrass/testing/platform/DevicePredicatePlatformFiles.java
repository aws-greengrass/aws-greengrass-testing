/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class DevicePredicatePlatformFiles implements PlatformFiles {
    private static final Logger LOGGER = LogManager.getLogger(DevicePredicatePlatformFiles.class);
    private final Device device;
    private final PlatformFiles left;
    private final PlatformFiles right;
    private final Predicate<Device> isLeft;

    /**
     * An "or" based predicate to delegate {@link PlatformFiles} implementations upon.
     *
     * @param isLeft A {@link Predicate} to switch which {@link PlatformFiles} implementation
     * @param device An underlying {@link Device} entity to test
     * @param left A {@link PlatformFiles} implementation to use if isLeft is true
     * @param right A {@link PlatformFiles} implementation to use isLeft is false
     */
    public DevicePredicatePlatformFiles(
            final Predicate<Device> isLeft,
            final Device device,
            final PlatformFiles left,
            final PlatformFiles right) {
        this.isLeft = isLeft;
        this.device = device;
        this.left = left;
        this.right = right;
    }

    public static PlatformFiles localOrRemote(final Device device, final PlatformFiles remote) {
        return new DevicePredicatePlatformFiles(d -> d.type().equals("LOCAL"), device, new LocalFiles(device), remote);
    }

    private <T> T delegate(Function<PlatformFiles, T> thunk) {
        PlatformFiles files = right;
        if (isLeft.test(device)) {
            files = left;
        }
        LOGGER.debug("Using file provider {}", files);
        return thunk.apply(files);
    }

    @Override
    public boolean exists(Path filePath) throws CommandExecutionException {
        return delegate(files -> files.exists(filePath));
    }

    @Override
    public void copyTo(Path source, Path destination) throws CopyException {
        delegate(files -> {
            files.copyTo(source, destination);
            return null;
        });
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        delegate(files -> {
            files.makeDirectories(filePath);
            return null;
        });
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        delegate(files -> {
            files.delete(filePath);
            return null;
        });
    }

    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        return delegate(files -> files.readBytes(filePath));
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        return delegate(files -> files.listContents(filePath));
    }
}
