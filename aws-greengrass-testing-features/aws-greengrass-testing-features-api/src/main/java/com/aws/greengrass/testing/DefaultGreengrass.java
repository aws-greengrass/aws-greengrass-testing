/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class DefaultGreengrass implements Greengrass {
    private static final Logger LOGGER = LogManager.getLogger(DefaultGreengrass.class);
    private static final long TIMEOUT_IN_SECONDS = 30L;
    private final AWSResourcesContext resourcesContext;
    private final Platform platform;
    private final GreengrassContext greengrassContext;
    private int greengrassProcess;
    private final TestContext testContext;
    private final WaitSteps waits;

    /**
     * Creates a {@link Greengrass} software instance.
     *
     * @param platform Abstract {@link Platform}
     * @param resourcesContext the global {@link AWSResourcesContext} for the test run
     * @param greengrassContext the global {@link GreengrassContext} for the test suite
     * @param testContext The underlying {@link TestContext}
     * @param waits The underlying {@link WaitSteps}
     */
    public DefaultGreengrass(
            final Platform platform,
            AWSResourcesContext resourcesContext,
            GreengrassContext greengrassContext,
            TestContext testContext,
            WaitSteps waits) {
        this.platform = platform;
        this.resourcesContext = resourcesContext;
        this.greengrassContext = greengrassContext;
        this.testContext = testContext;
        this.waits = waits;
    }

    private boolean isRunning() {
        if (testContext.initializationContext().persistInstalledSoftware()) {
            return true;
        }
        return greengrassProcess != 0;
    }

    private boolean isRegistered() {
        return platform.files().exists(testContext.installRoot()
                .resolve("config").resolve("effectiveConfig.yaml"));
    }

    @Override
    public void install() {
        if (!isRegistered()) {
            System.out.println("install greengrass");
            System.out.println(platform.files().getClass());
            platform.files().copyTo(
                    greengrassContext.greengrassPath(),
                    testContext.installRoot().resolve("greengrass"));
            platform.commands().execute(CommandInput.builder()
                    .line("java")
                    .addArgs(
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
    }

    @Override
    public void start() {
        System.out.println("start greengrass");
        if (!isRunning()) {
            Path loaderPath = null;
            if (PlatformOS.currentPlatform().isWindows()) {
                loaderPath = testContext.installRoot().resolve("alts/current/distro/bin/loader");
                System.out.println("loaderPath: " + loaderPath);
            } else {
                loaderPath = testContext.installRoot().resolve("alts/current/distro/bin/loader");
                System.out.println("loaderPath: " + loaderPath);
            }
            platform.commands().makeExecutable(testContext.installRoot().resolve(loaderPath));
            System.out.println("after makeExecutable");
            greengrassProcess = platform.commands().executeInBackground(CommandInput.builder()
                    .workingDirectory(testContext.installRoot())
                    .line(loaderPath.toString())
                    .timeout(TIMEOUT_IN_SECONDS)
                    .build());
            LOGGER.info("Starting Greengrass on pid {}", greengrassProcess);
        }
    }

    @Override
    public synchronized void stop() {
        try {
            if (testContext.cleanupContext().persistInstalledSoftware()) {
                LOGGER.info("Leaving Greengrass running on pid: {}", greengrassProcess);
                greengrassProcess = 0;
            }
            if (greengrassProcess > 0) {
                platform.commands().killAll(greengrassProcess);
                if (!waits.untilTrue(() -> platform.commands().findDescendants(greengrassProcess).size() == 1,
                        30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Failed to successfully remove the Greengrass process "
                            + greengrassProcess);
                }
                LOGGER.info("Stopped Greengrass on pid {}", greengrassProcess);
                greengrassProcess = 0;
            }
        } catch (CommandExecutionException e) {
            LOGGER.warn("Failed to kill Greengrass process {}: {}", greengrassProcess, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
