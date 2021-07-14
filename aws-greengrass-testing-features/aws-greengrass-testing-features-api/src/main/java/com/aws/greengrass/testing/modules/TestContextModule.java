/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import javax.inject.Singleton;

@AutoService(Module.class)
public class TestContextModule extends AbstractModule {
    private static final String TEST_RESULTS_PATH = "test.log.path";
    private static final String TEST_ID_PREFIX = "test.id.prefix";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String randomString(int size) {
        final byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(16).substring(size);
    }

    @Provides
    @Singleton
    static TimeoutMultiplier providesTimeoutMultiplier() {
        return TimeoutMultiplier.builder().build();
    }

    @Provides
    @ScenarioScoped
    static TestId providesTestId() {
        return TestId.builder()
                .prefix(System.getProperty(TEST_ID_PREFIX, ""))
                .id(randomString(20)) // This should probably be replaced too
                .build();
    }

    @Provides
    @ScenarioScoped
    static TestContext providesTestContext(
            final TestId testId,
            final CleanupContext cleanupContext,
            final GreengrassContext greengrassContext) {
        Path testDirectory = greengrassContext.tempDirectory().resolve(testId.prefixedId());
        Path testResultsPath = Paths.get(System.getProperty(TEST_RESULTS_PATH, "testResults"));
        try {
            Files.createDirectory(testDirectory);
            Files.createDirectories(testResultsPath);
        } catch (IOException ie) {
            throw new ModuleProvisionException(ie);
        }
        return TestContext.builder()
                .testId(testId)
                .testResultsPath(testResultsPath)
                .testDirectory(testDirectory)
                .cleanupContext(cleanupContext)
                .build();
    }
}
