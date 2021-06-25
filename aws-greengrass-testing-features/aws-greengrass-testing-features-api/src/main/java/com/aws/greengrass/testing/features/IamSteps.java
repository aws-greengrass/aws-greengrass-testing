/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import javax.inject.Named;

@ScenarioScoped
public class IamSteps {
    private static final String DEFAULT_CONFIG = "/iam/configs/basic_config.yaml";
    private final AWSResources resources;
    private final ObjectMapper mapper;
    private final TestId testId;
    private final AWSResourcesContext context;

    @Inject
    IamSteps(
            TestId testId,
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            AWSResourcesContext context,
            AWSResources resources) {
        this.resources = resources;
        this.mapper = mapper;
        this.testId = testId;
        this.context = context;
    }

    /**
     * Create a default IAM role to get a working {@link com.aws.greengrass.testing.api.Greengrass} instance.
     *
     * @return IamRoleSpec
     * @throws RuntimeException failed to a default IAM policy
     */
    @Given("I create a default IAM role for Greengrass")
    public IamRoleSpec createDefaultIamRole() {
        try {
            return createIamRole(DEFAULT_CONFIG);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an IAM role from a configuration file.
     *
     * @param roleFile configuration file to create an IAM role from
     * @return IamRoleSpec
     * @throws IOException failed to read configuration from a file
     */
    @Given("I create an IAM role from {word}")
    public IamRoleSpec createIamRole(String roleFile) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(roleFile)) {
            IamRoleSpec spec = mapper.readValue(in, IamRoleSpec.class);
            return resources.create(IamRoleSpec.builder().from(spec)
                    .roleName(testId.idFor(spec.roleName()))
                    .trustDocument(spec.trustDocument().replace("{stage}",
                            context.isProd() ? "" : ".test"))
                    .build());
        }
    }
}
