package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.ScenarioTestRuns;
import com.aws.greengrass.testing.api.TestRuns;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

import javax.inject.Singleton;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

@AutoService(Module.class)
public class TestContextModule extends AbstractModule {
    private static final String TEST_RESULTS_PATH = "test.log.path";
    private static final SecureRandom RANDOM = new SecureRandom();

    static String randomString(int size) {
        final byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(16).substring(size);
    }

    @Provides
    @Singleton
    static TestRuns providesTestRunTracker() {
        return new ScenarioTestRuns();
    }

    @Provides
    @ScenarioScoped
    static TestId providesTestId() {
        return TestId.builder()
                .id(randomString(20))
                .build();
    }

    @Provides
    @ScenarioScoped
    static TestContext providesTestContext(
            final TestId testId,
            final CleanupContext cleanupContext) {
        Path testDirectory = Paths.get(testId.id());
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