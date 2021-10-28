/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features.secret;

import com.aws.greengrass.testing.features.DeploymentSteps;
import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.secret.SecretLifecycle;
import com.aws.greengrass.testing.resources.secret.SecretSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private String secretArn;
    private final ObjectMapper mapper;
    private final DeploymentSteps deploySteps;



    @Inject
    SecretSteps(
            final AWSResources resources,
            final TestContext testContext,
            DeploymentSteps deploySteps,
            final ScenarioContext scenarioContext,
            final RegistrationContext registrationContext,
            final WaitSteps waits,
            final IotSteps iotSteps,
            final ObjectMapper mapper
            ) {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.resources = resources;
        this.iotSteps = iotSteps;
        this.waits = waits;
        this.mapper = mapper;
        this.deploySteps = deploySteps;
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
        SecretSpec update = resources.create(SecretSpec.builder()
                .secretId(secretId).secretValue(secretString).build());
        secretArn = update.resource().secretArn();
    }

    /**
     * Update secret config.
     * @throws JsonProcessingException exception
     */
    @When("I update secrets manager with configured secrets")
    public void updateConfig() throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();
        String secretString = mapper.writeValueAsString(getSecretConfiguration());
        deploySteps.updateDeployment("aws.greengrass.SecretManager", secretString);
    }

    private Map<String, Map<String, Object>> getSecretConfiguration() {
        Map<String, Map<String, Object>> configuration = new HashMap<>();
        configuration.put("MERGE", new HashMap<>());

        String createdArn = secretArn;
        Map<String, Object> secretKeyToValue = new HashMap<>();
        secretKeyToValue.putIfAbsent("arn", createdArn);
        configuration.get("MERGE").put("cloudSecrets", Collections.singletonList(secretKeyToValue));
        return configuration;
    }


}
