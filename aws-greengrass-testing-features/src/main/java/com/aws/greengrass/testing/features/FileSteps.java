package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class FileSteps {
    private static final long DEFAULT_TIMEOUT = 10L;
    private final Device device;
    private final Platform platform;
    private final TestContext testContext;

    @Inject
    public FileSteps(
            Device device,
            Platform platform,
            TestContext testContext) {
        this.device = device;
        this.platform = platform;
        this.testContext = testContext;
    }

    @Then("the file {word} on device exists")
    public void checkFileExists(String file) {
        assertTrue(device.exists(testContext.testDirectory().resolve(file)),
                "file " + file + " does not exist in " + testContext.testDirectory());
    }

    @Then("the file {word} on device contains {word}")
    public void contains(String file, String contents) throws InterruptedException {
        containsTimeout(file, contents, DEFAULT_TIMEOUT);
    }

    @Then("the file {word} on device contains {word} within {int} seconds")
    public void containsTimeout(String file, String contents, long seconds) throws InterruptedException {
        checkFileExists(file);
        boolean found = false;
        do {
            found = platform.files().readString(testContext.testDirectory().resolve(file)).contains(contents);
            if (found) {
                break;
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
        } while (!found);
        assertTrue(found, "file " + file + " did not contain " + contents);
    }
}
