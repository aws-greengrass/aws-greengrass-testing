/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features.secret;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.aws.greengrass.testing.resources.s3.S3BucketSpec;
import com.aws.greengrass.testing.resources.secret.SecretLifecycle;
import com.aws.greengrass.testing.resources.secret.SecretSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;


@ScenarioScoped
public class SecretSteps {

    private static final Logger LOGGER = LogManager.getLogger(SecretSteps.class);
    private static final short PORT = 443;
    private final TestContext testContext;
    private final ScenarioContext scenarioContext;
    private final AWSResources resources;
    private final IotSteps iotSteps;
    private final RegistrationContext registrationContext;
    private final WaitSteps waits;



    @Inject
    SecretSteps(
            final AWSResources resources,
            final TestContext testContext,
            final ScenarioContext scenarioContext,
            final RegistrationContext registrationContext,
            final WaitSteps waits,
            final IotSteps iotSteps) {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.resources = resources;
        this.iotSteps = iotSteps;
        this.waits = waits;
    }


    /**
     * Create an secret.
     *
     * @param secretId secret id
     *
     * @param secretString secret value
     */

    @When("I create a secret named {word} with value {word}")
    public void createSecret(String secretId, String secretString) {
        SecretLifecycle secret = resources.lifecycle(SecretLifecycle.class);
        resources.create(SecretSpec.builder().secretId(secretId).secretValue(secretString).build());


    }


}
