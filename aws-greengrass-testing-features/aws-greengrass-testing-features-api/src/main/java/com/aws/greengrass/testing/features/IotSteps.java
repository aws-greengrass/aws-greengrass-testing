package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@ScenarioScoped
public class IotSteps {
    private static final String DEFAULT_POLICY_CONFIG = "/iot/configs/basic_policy.yaml";
    private final AWSResources resources;
    private final ObjectMapper mapper;
    private final TestId testId;

    @Inject
    public IotSteps(
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

    @Given("I create an IoT policy from {word}")
    public IotPolicySpec createPolicy(String config) throws IOException {
        return createPolicy(config, null);
    }

    public IotPolicySpec createDefaultPolicy(String policyNameOverride) {
        try {
            return createPolicy(DEFAULT_POLICY_CONFIG, policyNameOverride);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
