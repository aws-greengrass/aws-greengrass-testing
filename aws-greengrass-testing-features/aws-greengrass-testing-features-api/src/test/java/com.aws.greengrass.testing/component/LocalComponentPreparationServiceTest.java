/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class LocalComponentPreparationServiceTest {
    private static final String MOCK_COMPONENT_C_NAME = "com.aws.MockComponentC";
    private static final String MOCK_COMPONENT_C_TYPE = "local";
    private static final String MOCK_COMPONENT_C_VERSION = "1.0.0";
    private static final String MOCK_TEST_ID = "mock_id";
    private static final String COMPONENT_C_RECIPE_PATH = "greengrass/components/recipes/local_component_C.yaml";
    private static final Path MOCK_COMPONENT_C_COMPONENT_PATH = Paths.get("mocktemp", MOCK_TEST_ID,
            "components", MOCK_COMPONENT_C_NAME);

    private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @InjectMocks
    LocalComponentPreparationService componentPreparation;

    @Mock
    LocalComponentPreparationService.ContentLoader loader;

    @Mock
    Platform platform;

    @Mock
    TestContext testContext;

    @Mock
    GreengrassContext greengrassContext;

    private Path resourceDirectory;

    @BeforeEach
    public void setup() {
        resourceDirectory = Paths.get(System.getProperty("user.dir"),"src", "test", "resources");
        this.componentPreparation = Mockito.spy(new LocalComponentPreparationService(platform, testContext,
                objectMapper, greengrassContext));
    }

    @Test
    void GIVEN_component_override_name_version_WHEN_it_is_local_component_THEN_return_an_expected_componentOverrideNameVersion() throws IOException {
        Path absolutePathForComponentC = resourceDirectory.resolve(COMPONENT_C_RECIPE_PATH);

        // Input for prepare()
        ComponentOverrideNameVersion overrideNameVersion = ComponentOverrideNameVersion.builder()
                .name(MOCK_COMPONENT_C_NAME)
                .version(ComponentOverrideVersion.of(MOCK_COMPONENT_C_TYPE, COMPONENT_C_RECIPE_PATH))
                .build();

        Mockito.doReturn(new FileInputStream(absolutePathForComponentC.toString()))
                .when(loader).load(Mockito.any());
        Mockito.doReturn(loader)
                .when(componentPreparation).getLoader();
        Mockito.doNothing()
                .when(componentPreparation).copyArtifactToLocalStore(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(componentPreparation).copyRecipeToLocalStore(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(MOCK_COMPONENT_C_COMPONENT_PATH)
                .when(componentPreparation).writeFileContentToFilePath(Mockito.any(), Mockito.any());

        Optional<ComponentOverrideNameVersion> res = componentPreparation.prepare(overrideNameVersion);

        assertTrue(res.isPresent());
        ComponentOverrideNameVersion componentOverrideNameVersion= res.get();
        assertEquals(MOCK_COMPONENT_C_VERSION, componentOverrideNameVersion.version().value());
        assertEquals(MOCK_COMPONENT_C_TYPE, componentOverrideNameVersion.version().type());
        assertEquals(MOCK_COMPONENT_C_NAME, componentOverrideNameVersion.name());
    }
}
