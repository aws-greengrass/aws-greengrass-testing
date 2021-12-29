/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;

@ScenarioScoped
public class IotSteps {
    private static final String DEFAULT_POLICY_CONFIG = "/iot/configs/basic_policy.yaml";
    private final AWSResources resources;
    private final ObjectMapper mapper;
    private final TestId testId;

    @Inject
    IotSteps(
            final TestId testId,
            final AWSResources resources,
            @Named(JacksonModule.YAML) final ObjectMapper mapper) {
        this.testId = testId;
        this.resources = resources;
        this.mapper = mapper;
    }

    @Given("I create the default IoT policy for Greengrass")
    public IotPolicySpec createDefaultPolicy() {
        return createDefaultPolicy(null);
    }

    /**
     * Create the default IoT policy with a name override.
     *
     * @param policyNameOverride name to use for IoT policy
     * @return IotPolicySpec
     * @throws RuntimeException failed to create an IoT policy for some reason
     */
    public IotPolicySpec createDefaultPolicy(String policyNameOverride) {
        try {
            return createPolicy(DEFAULT_POLICY_CONFIG, policyNameOverride);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new IoT policy using a configuration name.
     *
     * @param config name of the configuration for the policy
     * @return IotPolicySpec
     * @throws IOException failed to create a policy from configuration
     */
    @Given("I create an IoT policy from {word}")
    public IotPolicySpec createPolicy(String config) throws IOException {
        return createPolicy(config, null);
    }

    /**
     * Create an IoT policy with a configuration and name override.
     *
     * @param config name of the config
     * @param policyNameOverride override of the policy name
     * @return IotPolicySpec
     * @throws IOException failed to create a configuration
     */
    public IotPolicySpec createPolicy(String config, String policyNameOverride) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(config)) {
            IotPolicySpec spec = mapper.readValue(in, IotPolicySpec.class);
            return resources.create(IotPolicySpec.builder()
                    .from(spec)
                    .policyName(testId.idFor(Optional.ofNullable(policyNameOverride).orElseGet(spec::policyName)))
                    .build());
        }
    }
}
