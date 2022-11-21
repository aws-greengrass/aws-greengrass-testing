/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.features.FileSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.NucleusInstallationParameters;
import com.aws.greengrass.testing.platform.Platform;
import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private FileSteps fileSteps;

    /**
     * Creates a {@link Greengrass} software instance.
     *
     * @param platform Abstract {@link Platform}
     * @param resourcesContext the global {@link AWSResourcesContext} for the test run
     * @param greengrassContext the global {@link GreengrassContext} for the test suite
     * @param testContext The underlying {@link TestContext}
     * @param waits The underlying {@link WaitSteps}
     * @param fileSteps The underlying {@link FileSteps}
     */
    public DefaultGreengrass(
            final Platform platform,
            AWSResourcesContext resourcesContext,
            GreengrassContext greengrassContext,
            TestContext testContext,
            WaitSteps waits,
            FileSteps fileSteps) {
        this.platform = platform;
        this.resourcesContext = resourcesContext;
        this.greengrassContext = greengrassContext;
        this.testContext = testContext;
        this.waits = waits;
        this.fileSteps = fileSteps;
    }

    @VisibleForTesting
    int getGreengrassProcess() {
        return this.greengrassProcess;
    }

    @VisibleForTesting
    boolean isRunning() {
        if (testContext.initializationContext().persistInstalledSoftware()) {
            return true;
        }
        return greengrassProcess != 0;
    }

    @VisibleForTesting
    boolean isRegistered() {
        return testContext.initializationContext().persistInstalledSoftware()
                && platform.files().exists(testContext.installRoot()
                        .resolve("config").resolve("effectiveConfig.yaml"));
    }

    @VisibleForTesting
    String getAWSRegion() {
        return resourcesContext.region().metadata().id();
    }

    @VisibleForTesting
    String getEnvStage() {
        return resourcesContext.envStage();
    }

    @Override
    public void install() {
        if (!isRegistered()) {
            platform.files().copyTo(
                    greengrassContext.greengrassPath(),
                    testContext.installRoot().resolve("greengrass"));

            Map<String, String> systemProperties = new HashMap<>();
            systemProperties.put("root", testContext.installRoot().toString());
            systemProperties.put("log.store", "FILE");
            systemProperties.put("log.level", testContext.logLevel());

            Map<String, String> ggParameters = new HashMap<>();
            ggParameters.put("--aws-region", getAWSRegion());
            ggParameters.put("--env-stage", getEnvStage());
            if (!testContext.currentUser().isEmpty()) {
                ggParameters.put("--component-default-user", testContext.currentUser());
            }
            if (!testContext.trustedPluginsPaths().isEmpty()) {
                for (String trustedPluginsPath : testContext.trustedPluginsPaths()) {
                    Path hostPath = Paths.get(trustedPluginsPath);
                    Path dutPath = testContext.installRoot().resolve(hostPath.getFileName());
                    try {
                        platform.files().copyTo(hostPath, dutPath);
                    } catch (CopyException e) {
                        LOGGER.error("Caught exception while copying file to DUT");
                        throw new RuntimeException(e);
                    }
                    ggParameters.put("--trusted-plugin", dutPath.toString());
                }
            }
            NucleusInstallationParameters nucleusInstallationParameters = NucleusInstallationParameters.builder()
                    .systemProperties(systemProperties)
                    .greengrassParameters(ggParameters)
                    .greengrassRootDirectoryPath(testContext.installRoot())
                    .build();
            platform.commands().installNucleus(nucleusInstallationParameters);
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
                    LOGGER.error("The pids of descendants of greengrass process still active are "
                            + platform.commands().findDescendants(greengrassProcess));
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
