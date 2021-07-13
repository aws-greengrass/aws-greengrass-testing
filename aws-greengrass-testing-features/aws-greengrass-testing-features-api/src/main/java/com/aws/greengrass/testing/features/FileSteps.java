/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

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

@ScenarioScoped
public class FileSteps {
    private static final Logger LOGGER = LogManager.getLogger(FileSteps.class);
    private static final int DEFAULT_TIMEOUT = 30;
    private final Platform platform;
    private final TestContext testContext;
    private final WaitSteps waits;

    @Inject
    FileSteps(
            Platform platform,
            TestContext testContext,
            WaitSteps waits) {
        this.platform = platform;
        this.testContext = testContext;
        this.waits = waits;
    }

    /**
     * Checks that a file exists on the DUT.
     *
     * @param file path relative to the install root in the TestContext
     * @throws IllegalStateException if the file does not exist on the DUT
     */
    @Then("the file {word} on device exists")
    public void checkFileExists(String file) {
        if (!platform.files().exists(testContext.installRoot().resolve(file))) {
            throw new IllegalStateException("file " + file + " does not exist in " + testContext.installRoot());
        }
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
        if (!found) {
            throw new IllegalStateException("file " + file + " did not contain " + contents);
        }
    }

    /**
     * Verifies that a component log file contains the contents within an interval.
     *
     * @param component name of the component log
     * @param line contents to validate
     * @param value number of units
     * @param unit specific {@link TimeUnit}
     * @throws InterruptedException throws when thread is interrupted
     */
    @Then("the {word} log on the device contains the line {string} within {int} {word}")
    public void logContains(String component, String line, int value, String unit) throws InterruptedException {
        containsTimeout("logs/" + component + ".log", line, value, unit);
    }

    /**
     * Verifies that a compoennt log does not contain a line.
     *
     * @param component name of the component log
     * @param line value the log file should not contain
     * @throws IllegalStateException throws if the log contains the line
     */
    @Then("the {word} log on the device not contains the line {string}")
    public void logNotContains(String component, String line) {
        if (platform.files().readString(testContext.installRoot().resolve("logs").resolve(component + ".log"))
                .contains(line)) {
            throw new IllegalStateException(component + " log contains '" + line + "'");
        }
    }

    /**
     * Copy logs for the {@link Scenario} from the {@link Device} to the host.
     *
     * @param scenario the unique {@link Scenario}
     */
    @After(order = 99899)
    public void copyLogs(final Scenario scenario) {
        Path logFolder = testContext.installRoot().resolve("logs");
        if (platform.files().exists(logFolder)) {
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
