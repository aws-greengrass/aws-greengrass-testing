package com.aws.greengrass.testing.examples.standalone;

import com.aws.greengrass.testing.api.model.TestId;
import com.google.inject.Inject;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class ExampleSteps implements Closeable {
    private static final String FILE_CONTENTS = "This is the secret contents: ";
    private final TestId testId;
    private final Path testDirectory;
    private Path testFile;

    @Inject
    ExampleSteps(final TestId testId, final Path testDirectory) {
        this.testId = testId;
        this.testDirectory = testDirectory;
    }

    /**
     * Prepares scenario for testing.
     *
     * @throws IOException fails to prepare the scenario
     */
    @Given("I have created the test directory")
    public void createTestDirectory() throws IOException {
        assertFalse(Files.exists(testDirectory));
        Files.createDirectories(testDirectory);
        assertTrue(Files.exists(testDirectory));
    }

    @When("I create a test file named {word} in the test directory")
    public void createTestFile(String name) throws IOException {
        testFile = testDirectory.resolve(name + ".txt");
        Files.write(testFile, (FILE_CONTENTS + testId.idFor("contents")).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validate that a test file is created.
     *
     * @throws IOException fails to validate file
     */
    @Then("the file is created with test information")
    public void validateTestFile() throws IOException {
        assertNotNull(testFile, "Create the file with 'I create a test file...'");
        assertTrue(Files.exists(testFile));
        assertEquals(FILE_CONTENTS + testId.idFor("contents"),
                new String(Files.readAllBytes(testFile), StandardCharsets.UTF_8));
    }

    @After
    @Override
    public void close() throws IOException {
        if (Objects.nonNull(testFile)) {
            Files.deleteIfExists(testFile);
        }
        Files.deleteIfExists(testDirectory);
    }
}
