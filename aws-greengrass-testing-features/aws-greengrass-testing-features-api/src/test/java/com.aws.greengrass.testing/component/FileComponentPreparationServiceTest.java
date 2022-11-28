/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileComponentPreparationServiceTest {

    @Mock
    AWSResources resources;

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Mock
    TestContext testContext;

    @Mock
    GreengrassContext greengrassContext;

    @Mock
    ComponentOverrides overrides;

    @InjectMocks
    FileComponentPreparationService fileComponentPreparationService = Mockito.spy(
            new FileComponentPreparationService(resources, mapper, testContext, greengrassContext, overrides));

    @Test
    void GIVEN_a_FileComponentPreparationService_class_inherits_RecipeComponentPreparationService_class_WHEN_a_FileComponentPreparationService_instance_is_initialized_THEN_it_inherit_properly() {
        assertEquals(fileComponentPreparationService.getClass().getSuperclass().getName(), RecipeComponentPreparationService.class.getName());
    }
}
