/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassComponent;
import com.aws.greengrass.testing.resources.greengrass.GreengrassComponentSpec;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class RecipeComponentPreparationServiceTest {

    private static final String MOCK_COMPONENT_A_NAME = "com.aws.MockComponentA";
    private static final String MOCK_COMPONENT_A_VERSION = "1.0.1";
    private static final String MOCK_COMPONENT_A_TYPE = "classpath";
    private static final String MOCK_TEST_ID = "mock_id";
    private static final String MOCK_BUCKET_NAME = "mock_bucket_name";
    private static final String MOCK_ARTIFACT_URI = "mockArtifactURI";
    private static final String MOCK_COMPONENT_ARN = "mock_arn";
    private static final String COMPONENT_A_RECIPE_PATH = "greengrass/components/recipes/local_component_A.yaml";

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    GreengrassV2Lifecycle greengrassV2Lifecycle = new GreengrassV2Lifecycle();

    @Mock
    TestContext testContext;

    @Mock
    TestId testId;

    @Mock
    GreengrassContext greengrassContext;

    @Mock
    ComponentOverrides overrides;

    @Mock
    RecipeComponentPreparationService.ContentLoader loader;

    @Mock
    AWSResources resources;

    @InjectMocks
    RecipeComponentPreparationService componentPreparation;

    private Path resourceDirectory;

    @BeforeEach
    public void setup() {
        resourceDirectory = Paths.get(System.getProperty("user.dir"),"src", "test", "resources");
        this.componentPreparation = Mockito.spy(new RecipeComponentPreparationService(loader,
                resources, mapper, testContext, greengrassContext, overrides));
    }

    @Test
    void GIVEN_component_override_name_version_for_component_A_WHEN_it_has_classpath_type_and_be_prepared_THEN_return_an_expected_componentOverrideNameVersion() throws IOException {
        Path absolutePathForComponentA = resourceDirectory.resolve(COMPONENT_A_RECIPE_PATH);

        // Input for prepare()
        ComponentOverrideNameVersion overrideNameVersion = ComponentOverrideNameVersion.builder()
                .name(MOCK_COMPONENT_A_NAME)
                .version(ComponentOverrideVersion.of(MOCK_COMPONENT_A_TYPE, COMPONENT_A_RECIPE_PATH))
                .build();

        Mockito.doReturn(new FileInputStream(absolutePathForComponentA.toString())).when(loader).load(Mockito.any());
        Mockito.doReturn(MOCK_TEST_ID).when(testId).id();
        Mockito.doReturn(testId).when(testContext).testId();
        Mockito.doReturn(greengrassV2Lifecycle).when(resources).lifecycle(GreengrassV2Lifecycle.class);
        Mockito.doReturn(true).when(componentPreparation).isArtifactExists(Mockito.any());
        Mockito.doReturn(MOCK_BUCKET_NAME).when(componentPreparation).getOrCreateBucket();
        Mockito.doReturn(MOCK_ARTIFACT_URI).when(componentPreparation)
                .uploadArtifact(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(GreengrassComponentSpec.builder()
                .resource(GreengrassComponent.builder()
                        .componentArn(MOCK_COMPONENT_ARN)
                        .componentVersion(MOCK_COMPONENT_A_VERSION)
                        .componentName(MOCK_COMPONENT_A_NAME)
                        .build())
                .build())
                .when(resources).create(Mockito.any());

        Optional<ComponentOverrideNameVersion> res = componentPreparation.prepare(overrideNameVersion);

        assertTrue(res.isPresent());
        ComponentOverrideNameVersion componentOverrideNameVersion= res.get();
        assertNotNull(componentOverrideNameVersion);
        assertEquals(MOCK_COMPONENT_A_VERSION, componentOverrideNameVersion.version().value());
        assertEquals(MOCK_COMPONENT_A_TYPE, componentOverrideNameVersion.version().type());
        assertEquals(MOCK_COMPONENT_A_NAME, componentOverrideNameVersion.name());

    }
}
