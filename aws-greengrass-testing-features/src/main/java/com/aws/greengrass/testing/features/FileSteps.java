package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.google.common.io.Files;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class FileSteps {
    private static final Logger LOGGER = LogManager.getLogger(FileSteps.class);
    private static final int DEFAULT_TIMEOUT = 30;
    private final Device device;
    private final Platform platform;
    private final TestContext testContext;
    private final WaitSteps waits;

    @Inject
    public FileSteps(
            Device device,
            Platform platform,
            TestContext testContext,
            WaitSteps waits) {
        this.device = device;
        this.platform = platform;
        this.testContext = testContext;
        this.waits = waits;
    }

    @Then("the file {word} on device exists")
    public void checkFileExists(String file) {
        assertTrue(device.exists(testContext.installRoot().resolve(file)),
                "file " + file + " does not exist in " + testContext.installRoot());
    }

    @Then("the file {word} on device contains {word}")
    public void contains(String file, String contents) throws InterruptedException {
        containsTimeout(file, contents, DEFAULT_TIMEOUT);
    }

    @Then("the file {word} on device contains {word} within {int} seconds")
    public void containsTimeout(String file, String contents, int seconds) throws InterruptedException {
        checkFileExists(file);
        boolean found = waits.untilTrue(() -> platform.files()
                .readString(testContext.installRoot().resolve(file)).contains(contents), seconds, TimeUnit.SECONDS);
        assertTrue(found, "file " + file + " did not contain " + contents);
    }

    @After(order = 99899)
    public void copyLogs(final Scenario scenario) {
        Path logFolder = testContext.installRoot().resolve("logs");
        if (device.exists(logFolder)) {
            platform.files().listContents(logFolder).forEach(logFile -> {
                byte[] bytes = platform.files().readBytes(logFile);
                scenario.attach(bytes, "text/plain", logFile.getFileName().toString());
                try {
                    Files.write(bytes, testContext.testResultsPath().resolve(logFile.getFileName()).toFile());
                } catch (IOException ie) {
                    LOGGER.warn("Could not copy {} into the results path {}",
                            logFile, testContext.testResultsPath(), ie);
                }
            });
            // Remove the rest
            platform.files().delete(testContext.installRoot());
        }
    }
}
