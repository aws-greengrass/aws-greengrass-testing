/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.inject.Inject;

@ScenarioScoped
public class FileSteps {
    private static final Logger LOGGER = LogManager.getLogger(FileSteps.class);
    private static final int DEFAULT_TIMEOUT = 30;
    private static final String DUT_PATH_PREFIX = "dut:";
    private final Platform platform;
    private final TestContext testContext;
    private final ScenarioContext scenarioContext;
    private final WaitSteps waits;
    private final SecureRandom random;
    private ArrayList<Path> logFiles = new ArrayList<>();

    private enum ByteNotation implements Function<Long, Long> {
        B(1),
        KB(1024),
        MB(1024 * 1024),
        GB(1024 * 1024 * 1024);

        long factor;

        ByteNotation(long factor) {
            this.factor = factor;
        }

        @Override
        public Long apply(Long value) {
            return value * factor;
        }
    }

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public FileSteps(
            Platform platform,
            TestContext testContext,
            ScenarioContext scenarioContext,
            WaitSteps waits) {
        this.platform = platform;
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.waits = waits;
        this.random = new SecureRandom();
        random.setSeed(System.currentTimeMillis());
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
        String componentPath = "logs/" + component + ".log";
        containsTimeout(componentPath, line, value, unit);
        if (testContext.initializationContext().persistInstalledSoftware()) {
            logFiles.add(testContext.installRoot().resolve(componentPath));
        }
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

    @ParameterType("B|KB|MB|GB")
    public ByteNotation bytes(String bytes) {
        return ByteNotation.valueOf(bytes);
    }

    /**
     * Creates a file on the host agent containing random bytes, with the name of the file and byte length specified
     * by the caller. <strong>Note</strong>: fileName can be interpolated with {@link ScenarioContext}, but will
     * resolve to the {@link TestContext}::testDirectory location.
     *
     * @param length number of bytes multiplied by the notation
     * @param notation human readable byte notation in the form of B, KB, MB, or GB respectively
     * @param fileName name of the file to generate
     * @throws IOException thrown for any IO exception for creating the random file
     */
    @Given("I create a random file that is {long}{bytes} large, named {word}")
    public void generateRandomlySizedFile(long length, ByteNotation notation, String fileName) throws IOException {
        Path filePath = testContext.testDirectory().resolve(scenarioContext.applyInline(fileName));
        if (Files.exists(filePath)) {
            throw new IllegalStateException("The file " + filePath + " already exists");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            SecureRandom random = new SecureRandom();
            random.ints(random.nextInt(127)).limit(notation.apply(length)).forEach(singleByte -> {
                try {
                    writer.write(singleByte);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to generate " + filePath + " that is " + length + notation
                            + " large.");
                }
            });
        }
    }

    /**
     * Copies any local file on the host agent to the device at a specified location.
     * <strong>Note</strong>: both localFile and remoteFile can be interpolated with {@link ScenarioContext} values.
     * The location of localFile will ultimately be rooted in {@link TestContext}::testDirectory.
     *
     * @param localFile the name of a file or folder to copy to the device
     * @param remoteFile the full path of the file or folder on the device
     */
    @When("I copy the file {word} to my device at {word}")
    public void copyLocalFileToRemoteLocation(String localFile, String remoteFile) {
        Path localPath = testContext.testDirectory().resolve(scenarioContext.applyInline(localFile));
        Path remotePath = Paths.get(scenarioContext.applyInline(remoteFile));
        if (Files.notExists(localPath)) {
            throw new IllegalStateException("The local file " + localPath + " does not exist");
        }
        platform.files().copyTo(localPath, remotePath);
    }


    /**
     * Copy logs for the {@link Scenario} from the {@link Device} to the host.
     *
     * @param scenario the unique {@link Scenario}
     */
    @After(order = 99899)
    public void copyLogs(final Scenario scenario) {
        Path logFolder = testContext.installRoot().resolve("logs");
        if (testContext.cleanupContext().persistInstalledSoftware()) {
            LOGGER.info("Stopping Greengrass service..");
            platform.commands().stopGreengrassService();
        }
        if (platform.files().exists(logFolder)) {
            platform.files().listContents(logFolder).forEach(logFile -> {
                byte[] bytes = platform.files().readBytes(logFile);
                scenario.attach(bytes, "text/plain", logFile.getFileName().toString());
                try {
                    Files.write(testContext.testResultsPath().resolve(logFile.getFileName()), bytes);
                    if (testContext.initializationContext().persistInstalledSoftware()) {
                        if (logFiles.contains(logFile)) {
                            platform.files().delete(logFile);
                        }
                    }

                } catch (IOException ie) {
                    LOGGER.warn("Could not copy {} into the results path {}",
                            logFile, testContext.testResultsPath(), ie);
                }
            });
            if (!testContext.cleanupContext().persistInstalledSoftware()) {
                // Remove the rest
                platform.files().delete(testContext.installRoot());
            } else {
                //Start greengrass after log cleanup
                LOGGER.info("Starting Greengrass service..");
                platform.commands().startGreengrassService();
            }
        }
    }
}
