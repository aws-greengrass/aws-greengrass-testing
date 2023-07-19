/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.platform.PlatformResolver;
import com.aws.greengrass.testing.resources.AWSResources;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import javax.inject.Named;

public class ClasspathComponentPreparationService extends RecipeComponentPreparationService {
    @Inject
    public ClasspathComponentPreparationService(
            PlatformResolver platformResolver,
            AWSResources resources,
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            TestContext testContext,
            GreengrassContext greengrassContext,
            ComponentOverrides overrides) {
        super(platformResolver, ClasspathComponentPreparationService.class::getResourceAsStream,
                resources, mapper, testContext, greengrassContext, overrides);
    }
}
