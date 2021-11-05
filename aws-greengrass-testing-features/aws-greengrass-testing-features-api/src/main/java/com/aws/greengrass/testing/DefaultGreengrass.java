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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultGreengrass implements Greengrass {
    private static final Logger LOGGER = LogManager.getLogger(DefaultGreengrass.class);

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
        return testContext.initializationContext().persistInstalledSoftware()
                && platform.files().exists(testContext.installRoot()
                        .resolve("config").resolve("effectiveConfig.yaml"));
    }

    @Override
    public void install() {
        if (!isRegistered()) {
            Map<String, String> args = new HashMap<>();
            platform.files().copyTo(
                    greengrassContext.greengrassPath(),
                    testContext.installRoot().resolve("greengrass"));
            args.put("-Droot=", testContext.installRoot().toString());
            args.put("-Dlog.store=", "FILE");
            args.put("-Dlog.level=", testContext.logLevel());
            args.put("-jar", testContext.installRoot().resolve("greengrass/lib/Greengrass.jar").toString());
            args.put("--aws-region", resourcesContext.region().metadata().id());
            args.put("--env-stage", resourcesContext.envStage());
            args.put("--component-default-user", testContext.currentUser());
            platform.commands().installNucleus(testContext.installRoot(), args);
        }
    }

    @Override
    public void start() {
        if (!isRunning()) {
            greengrassProcess = platform.commands().startNucleus(testContext.installRoot());
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
