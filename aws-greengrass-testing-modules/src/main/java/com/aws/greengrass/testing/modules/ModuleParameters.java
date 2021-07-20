/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.Parameters;
import com.aws.greengrass.testing.api.model.Parameter;
import com.aws.greengrass.testing.api.model.PersistMode;
import com.google.auto.service.AutoService;

import java.util.Arrays;
import java.util.List;

@AutoService(Parameters.class)
public class ModuleParameters implements Parameters {
    static final String PERSIST_TESTING_RESOURCES = "gg.persist";
    static final String COMPONENT_BUCKET = "gg.component.bucket";
    static final String COMPONENT_OVERRIDES = "gg.component.overrides";
    static final String PROXY_URL = "proxy.url";
    static final String ENV_STAGE = "env.stage";
    static final String AWS_REGION = "aws.region";

    @Override
    public List<Parameter> available() {
        return Arrays.asList(
                Parameter.builder()
                        .name(COMPONENT_BUCKET)
                        .description("The name of an existing S3 bucket that houses Greengrass components.")
                        .build(),
                Parameter.builder()
                        .name(COMPONENT_OVERRIDES)
                        .description("A comma separated list of Greengrass component overrides. ex: "
                                + "aws.greengrass.LocalDebugConsole:^2.0.0,com.MyComponent:file/path/to/recipe.yaml")
                        .build(),
                Parameter.of(ENV_STAGE, "Targets the deployment environment of Greengrass. "
                        + "Defaults to production"),
                Parameter.of(AWS_REGION, "Targets specific regional endpoint for AWS services. "
                        + "Defaults to whatever the AWS SDK discovers."),
                Parameter.of(PROXY_URL, "Configure all tests to route traffic through this URL."),
                Parameter.of(PERSIST_TESTING_RESOURCES, "A comma separated list of test elements to persist "
                        + "after a test run. Default behavior is to persist nothing. Accepted values are: "
                        + Arrays.toString(PersistMode.values()))
        );
    }
}
