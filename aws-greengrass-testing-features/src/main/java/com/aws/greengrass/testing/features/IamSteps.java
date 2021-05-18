package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@ScenarioScoped
public class IamSteps {
    private static final String DEFAULT_CONFIG = "basic_config.yaml";
    private final AWSResources resources;
    private final ObjectMapper mapper;
    private final TestId testId;

    @Inject
    public IamSteps(TestId testId, ObjectMapper mapper, AWSResources resources) {
        this.resources = resources;
        this.mapper = mapper;
        this.testId = testId;
    }

    @Given.Givens({
            @Given("I create an IAM role from {word}"),
            @Given("I create a default IAM role for a greengrass core device")
    })
    public IamRoleSpec createIamRole(String roleFile) throws IOException {
        final String configFile = Optional.ofNullable(roleFile).orElse(DEFAULT_CONFIG);

        try (InputStream in = getClass().getResourceAsStream(configFile)) {
            IamRoleSpec spec = mapper.readValue(in, IamRoleSpec.class);
            return resources.create(IamRoleSpec.builder().from(spec)
                    .roleName(testId.idFor(spec.roleName()))
                    .build());
        }
    }

    public IamRoleSpec createDefaultIamRole() {
        try {
            return createIamRole(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
