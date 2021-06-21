package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class DockerSteps {
    private final Platform platform;

    @Inject
    public DockerSteps(final Platform platform) {
        this.platform = platform;
    }

    @Given("the docker image {word} does not exist on the device")
    public void checkDockerImageIsMissing(String image) {
        Predicate<Set<String>> predicate = Set::isEmpty;
        checkDockerImagePresence(image, predicate.negate(),
                "The image " + image + " is already on the device. Please remove the image and try again.");
    }

    @Then("I can check that the docker image {word} exists on the device")
    public void checkDockerImage(String image) {
        checkDockerImagePresence(image, Set::isEmpty, "Could not find image " + image + " on the device.");
    }

    private void checkDockerImagePresence(String image, Predicate<Set<String>> validity, String message) {
        // This could be improved by using the API on a local host.
        Set<String> parts = new HashSet<>(Arrays.stream(image.split(":")).collect(Collectors.toSet()));
        String[] result = platform.commands().executeToString(CommandInput.builder()
                .line("docker").addArgs("images")
                .build())
                .split("\\r?\\n");
        Arrays.stream(result)
                .map(String::trim)
                .flatMap(line -> Arrays.stream(line.split("\\s+")))
                .forEach(parts::remove);
        assertTrue(validity.test(parts), message);
    }
}
