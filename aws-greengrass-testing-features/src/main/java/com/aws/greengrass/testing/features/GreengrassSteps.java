package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ScenarioScoped
public class GreengrassSteps {
    private static final int MAX_BUFFER = 1_000_000;
    private final Greengrass greengrass;
    private final GreengrassContext greengrassContext;
    private final TestContext testContext;
    private final Device device;

    @Inject
    public GreengrassSteps(
            final Device device,
            final Greengrass greengrass,
            final GreengrassContext greengrassContext,
            final TestContext testContext) {
        this.device = device;
        this.greengrass = greengrass;
        this.greengrassContext = greengrassContext;
        this.testContext = testContext;
    }

    public void install() throws IOException {
        final Path stagingPath = testContext.testDirectory().resolve("greengrass");
        if (!Files.exists(stagingPath)) {
            Files.createDirectory(stagingPath);
            try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(stagingPath.toFile()))) {
                ZipEntry entry = zipStream.getNextEntry();
                while (Objects.nonNull(entry)) {
                    final Path contentPath = stagingPath.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(contentPath);
                        continue;
                    } else if (!Files.exists(contentPath.getParent())) {
                        Files.createDirectories(contentPath.getParent());
                    }
                    try (FileOutputStream output = new FileOutputStream(contentPath.toFile())) {
                        final byte[] buffer = new byte[MAX_BUFFER];
                        int read = 0;
                        do {
                            read = zipStream.read(buffer, 0, read);
                            if (read > 0) {
                                output.write(buffer, 0, read);
                            }
                        } while (read > 0);
                    }
                }
            }
            device.sync(stagingPath);
        }
    }

    @Given("my device is running Greengrass")
    @When("I start Greengrass")
    public void start() throws IOException {
        install();
        greengrass.start();
    }

    @When("I stop Greengrass")
    public void stop() {
        greengrass.stop();
    }

    @When("I restart Greengrass")
    public void restart() {
        greengrass.stop();
        greengrass.start();
    }
}
