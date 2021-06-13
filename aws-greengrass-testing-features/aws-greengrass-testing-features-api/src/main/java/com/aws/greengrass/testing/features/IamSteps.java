package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@ScenarioScoped
public class IamSteps {
    private static final String DEFAULT_CONFIG = "/iam/configs/basic_config.yaml";
    private final AWSResources resources;
    private final ObjectMapper mapper;
    private final TestId testId;

    @Inject
    public IamSteps(
            TestId testId,
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            AWSResources resources) {
        this.resources = resources;
        this.mapper = mapper;
        this.testId = testId;
    }

    @Given("I create a default IAM role for Greengrass")
    public IamRoleSpec createDefaultIamRole() {
        try {
            return createIamRole(DEFAULT_CONFIG);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Given("I create an IAM role from {word}")
    public IamRoleSpec createIamRole(String roleFile) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(roleFile)) {
            IamRoleSpec spec = mapper.readValue(in, IamRoleSpec.class);
            return resources.create(IamRoleSpec.builder().from(spec)
                    .roleName(testId.idFor(spec.roleName()))
                    .build());
        }
    }
}