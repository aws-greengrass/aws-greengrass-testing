/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;

@ScenarioScoped
public class DockerSteps {
    private static final Logger LOGGER = LogManager.getLogger(DockerSteps.class);
    private final Platform platform;
    private final Set<String> createdImages;

    @Inject
    DockerSteps(final Platform platform) {
        this.platform = platform;
        this.createdImages = new HashSet<>();
    }

    /**
     * Checks that the docker image does <strong>not</strong> exist on the {@link Platform} for this device.
     *
     * @param image fully qualified image name in <code>name:tag</code> representation
     */
    @Given("deleted the docker image {word} if exists on the device")
    public void checkDockerImageIsMissing(String image) {
        Predicate<Set<String>> predicate = Set::isEmpty;
        if (isDockerImagePresence(image, predicate.negate())) {
            removeDockerImage(image);
        }

    }

    @Then("I can check that the docker image {word} exists on the device")
    public void checkDockerImage(String image) {
        checkDockerImagePresence(image, Set::isEmpty, "Could not find image " + image + " on the device.");
        createdImages.add(image);
    }

    /**
     * Removes the docker image from the device.
     *
     * @param image fully qualified image name in <code>name:tag</code> representation
     */
    @When("I remove the docker image {word} on the device")
    public void removeDockerImage(String image) {
        String result = platform.commands().executeToString(CommandInput.builder()
                .line("docker").addArgs("rmi").addArgs(image)
                .build());
        LOGGER.debug("Removed docker image {}: {}", image, result);
    }

    private boolean isDockerImagePresence(String image, Predicate<Set<String>> validity) {
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

        if (!validity.test(parts)) {
            return false;
        }

        return true;
    }

    private void checkDockerImagePresence(String image, Predicate<Set<String>> validity, String message) {

        if (!isDockerImagePresence(image, validity)) {
            throw new IllegalStateException(message);
        }
    }

    @After
    public void removeCreatedImages() {
        createdImages.forEach(this::removeDockerImage);
    }
}
