/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class DefaultGreengrass implements Greengrass {
    private static final Logger LOGGER = LogManager.getLogger(DefaultGreengrass.class);
    private static final long TIMEOUT_IN_SECONDS = 30L;
    private final AWSResourcesContext resourcesContext;
    private final Platform platform;
    private int greengrassProcess;
    private final TestContext testContext;

    /**
     * Creates a {@link Greengrass} software instance.
     *
     * @param platform Abstract {@link Platform}
     * @param resourcesContext the global {@link AWSResourcesContext} for the test run
     * @param testContext The underlying {@link TestContext}
     */
    public DefaultGreengrass(
            final Platform platform,
            AWSResourcesContext resourcesContext,
            TestContext testContext) {
        this.platform = platform;
        this.resourcesContext = resourcesContext;
        this.testContext = testContext;
    }

    private boolean isRunning() {
        return greengrassProcess != 0;
    }

    @Override
    public void install() {
        platform.commands().execute(CommandInput.builder()
                .line("java").addArgs(
                        "-Droot=" + testContext.installRoot(),
                        "-Dlog.store=FILE",
                        "-Dlog.level=" + testContext.logLevel(),
                        "-jar", testContext.installRoot().resolve("greengrass/lib/Greengrass.jar").toString(),
                        "--aws-region", resourcesContext.region().metadata().id(),
                        "--env-stage", resourcesContext.envStage(),
                        "--start", "false")
                .timeout(TIMEOUT_IN_SECONDS)
                .build());
    }

    @Override
    public void start() {
        if (!isRunning()) {
            Path loaderPath = testContext.installRoot().resolve("alts/current/distro/bin/loader");
            platform.commands().makeExecutable(testContext.installRoot().resolve(loaderPath));
            greengrassProcess = platform.commands().executeInBackground(CommandInput.builder()
                    .workingDirectory(testContext.installRoot())
                    .line(loaderPath.toString())
                    .timeout(TIMEOUT_IN_SECONDS)
                    .build());
        }
        LOGGER.info("Starting Greengrass on pid {}", greengrassProcess);
    }

    @Override
    public synchronized void stop() {
        try {
            if (testContext.cleanupContext().persistInstalledSofware()) {
                LOGGER.info("Leaving Greengrass running on pid: {}", greengrassProcess);
                greengrassProcess = 0;
            }
            if (isRunning()) {
                platform.commands().killAll(greengrassProcess);
                LOGGER.info("Stopped Greengrass on pid {}", greengrassProcess);
                greengrassProcess = 0;
            }
        } catch (CommandExecutionException e) {
            LOGGER.warn("Failed to kill process {}: {}", greengrassProcess, e.getMessage());
        }
    }
}
