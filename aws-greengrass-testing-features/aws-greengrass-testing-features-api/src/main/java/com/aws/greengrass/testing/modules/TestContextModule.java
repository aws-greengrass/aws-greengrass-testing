/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
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
    private static final SecureRandom RANDOM = new SecureRandom();

    static String randomString(int size) {
        final byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(16).substring(size);
    }

    @Provides
    @Singleton
    static TimeoutMultiplier providesTimeoutMultiplier(final ParameterValues parameterValues) {
        double multiplier = parameterValues.getString(FeatureParameters.TIMEOUT_MULTIPLIER)
                .map(Double::parseDouble)
                .orElse(1.0);
        return TimeoutMultiplier.builder()
                .multiplier(multiplier)
                .build();
    }

    @Provides
    @ScenarioScoped
    static TestId providesTestId(ParameterValues parameterValues) {
        return TestId.builder()
                .prefix(parameterValues.getString(FeatureParameters.TEST_ID_PREFIX).orElse("gg"))
                .id(randomString(20)) // This should probably be replaced too
                .build();
    }

    @Provides
    @ScenarioScoped
    static TestContext providesTestContext(
            final ParameterValues parameterValues,
            final TestId testId,
            final CleanupContext cleanupContext,
            final InitializationContext initializationContext,
            final GreengrassContext greengrassContext) {
        Path testDirectory = greengrassContext.tempDirectory().resolve(testId.prefixedId());
        Path testResultsPath = parameterValues.getString(FeatureParameters.TEST_RESULTS_PATH)
                .map(Paths::get)
                .orElseGet(() -> Paths.get("testResults"));
        try {
            Files.createDirectory(testDirectory);
            Files.createDirectories(testResultsPath);
        } catch (IOException ie) {
            throw new ModuleProvisionException(ie);
        }
        String coreThingName = testId.idFor("ggc-thing");
        Path installPath = parameterValues.getString(FeatureParameters.NUCLEUS_INSTALL_ROOT)
                .map(s -> Paths.get(s, testDirectory.getFileName().toString()))
                .orElseGet(testDirectory::toAbsolutePath);
        if (initializationContext.persistInstalledSoftware()) {
            installPath = installPath.getParent();
            // TODO: improve this coupling, perhaps with a package visible field on GreengrassContext
            coreThingName = GreengrassContextModule.DEFAULT_CORE_THING_NAME;
        }
        return TestContext.builder()
                .logLevel(parameterValues.getString(FeatureParameters.NUCLEUS_LOG_LEVEL).orElse("INFO"))
                .currentUser(parameterValues.getString(FeatureParameters.NUCLEUS_USER)
                        .orElseGet(() -> System.getProperty("user.name")))
                .testId(testId)
                .testResultsPath(testResultsPath)
                .installRoot(installPath)
                .testDirectory(testDirectory)
                .cleanupContext(cleanupContext)
                .coreThingName(coreThingName)
                .initializationContext(initializationContext)
                .build();
    }
}
