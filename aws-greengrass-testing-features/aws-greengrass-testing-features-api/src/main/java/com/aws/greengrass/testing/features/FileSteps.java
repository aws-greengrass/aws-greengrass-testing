package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    FileSteps(
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

    @Then("the file {word} on device contains {string}")
    public void contains(String file, String contents) throws InterruptedException {
        containsTimeout(file, contents, DEFAULT_TIMEOUT, TimeUnit.SECONDS.name());
    }

    /**
     * File contains content after a duration.
     *
     * @param file file on a {@link Device}
     * @param contents file contents
     * @param value integer value for a duration
     * @param unit {@link TimeUnit} duration
     * @throws InterruptedException thread was interrupted while waiting
     */
    @Then("the file {word} on device contains {string} within {int} {word}")
    public void containsTimeout(String file, String contents, int value, String unit) throws InterruptedException {
        checkFileExists(file);
        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        boolean found = waits.untilTrue(() -> platform.files()
                .readString(testContext.installRoot().resolve(file)).contains(contents), value, timeUnit);
        assertTrue(found, "file " + file + " did not contain " + contents);
    }

    @Then("the {word} log on the device contains the line {string} within {int} {word}")
    public void logContains(String component, String line, int value, String unit) throws InterruptedException {
        containsTimeout("logs/" + component + ".log", line, value, unit);
    }

    @Then("the {word} log on the device not contains the line {string}")
    public void logNotContains(String component, String line) {
        assertFalse(platform.files().readString(testContext.installRoot().resolve("logs").resolve(component + ".log"))
                .contains(line), component + " log contains '" + line + "'");
    }

    /**
     * Copy logs for the {@link Scenario} from the {@link Device} to the host.
     *
     * @param scenario the unique {@link Scenario}
     */
    @After(order = 99899)
    public void copyLogs(final Scenario scenario) {
        Path logFolder = testContext.installRoot().resolve("logs");
        if (device.exists(logFolder)) {
            platform.files().listContents(logFolder).forEach(logFile -> {
                byte[] bytes = platform.files().readBytes(logFile);
                scenario.attach(bytes, "text/plain", logFile.getFileName().toString());
                try {
                    Files.write(testContext.testResultsPath().resolve(logFile.getFileName()), bytes);
                } catch (IOException ie) {
                    LOGGER.warn("Could not copy {} into the results path {}",
                            logFile, testContext.testResultsPath(), ie);
                }
            });
            if (!testContext.cleanupContext().persistInstalledSofware()) {
                // Remove the rest
                platform.files().delete(testContext.installRoot());
            }
        }
    }
}
