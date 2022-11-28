/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CompositeComponentPreparationServiceTest {
    private static final String CLOUD_KEY = "cloud";
    private static final String CLASSPATH_KEY = "classpath";
    private static final String LOCAL_KEY = "local";
    private static final String FILE_KEY = "file";

    private CloudComponentPreparationService cloudComponentPreparationService = Mockito.mock(CloudComponentPreparationService.class);
    private RecipeComponentPreparationService recipeComponentPreparationService = Mockito.mock(RecipeComponentPreparationService.class);
    private LocalComponentPreparationService localComponentPreparationService = Mockito.mock(LocalComponentPreparationService.class);
    private FileComponentPreparationService fileComponentPreparationService = Mockito.mock(FileComponentPreparationService.class);
    private ClasspathComponentPreparationService classpathComponentPreparationService = Mockito.mock(ClasspathComponentPreparationService.class);


    private Map<String, ComponentPreparationService> services = new HashMap<String, ComponentPreparationService>(){{
        put(CLOUD_KEY, cloudComponentPreparationService);
        put(CLASSPATH_KEY, classpathComponentPreparationService);
        put(LOCAL_KEY, localComponentPreparationService);
        put(FILE_KEY, fileComponentPreparationService);
    }};

    @InjectMocks
    CompositeComponentPreparationService componentPreparationService = Mockito.spy(new CompositeComponentPreparationService(services));

    @Test
    void GIVEN_user_wants_to_select_proper_preparation_service_WHEN_it_calls_CompositeComponentPreparationService_prepare_THEN_it_reroutes_user_to_the_proper_service() {
        // Scenario 1: cloud component
        ComponentOverrideNameVersion targetOverrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of(CLOUD_KEY, "NUCLEUS_VERSION"))
                .build();

        Mockito.doReturn(Optional.of(targetOverrideNameVersion)).when(cloudComponentPreparationService).prepare(Mockito.any());
        componentPreparationService.prepare(targetOverrideNameVersion);
        Mockito.verify(cloudComponentPreparationService, Mockito.times(1)).prepare(targetOverrideNameVersion);

        // Scenario 2: local component
        targetOverrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of(LOCAL_KEY, "NUCLEUS_VERSION"))
                .build();

        Mockito.doReturn(Optional.of(targetOverrideNameVersion)).when(localComponentPreparationService).prepare(Mockito.any());
        componentPreparationService.prepare(targetOverrideNameVersion);
        Mockito.verify(localComponentPreparationService, Mockito.times(1)).prepare(targetOverrideNameVersion);

        // Scenario 3: classpath component
        targetOverrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of(CLASSPATH_KEY, "NUCLEUS_VERSION"))
                .build();
        componentPreparationService.prepare(targetOverrideNameVersion);
        Mockito.verify(classpathComponentPreparationService, Mockito.times(1)).prepare(targetOverrideNameVersion);

        // Scenario 4: file component
        targetOverrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of(FILE_KEY, "NUCLEUS_VERSION"))
                .build();
        componentPreparationService.prepare(targetOverrideNameVersion);
        Mockito.verify(fileComponentPreparationService, Mockito.times(1)).prepare(targetOverrideNameVersion);

    }


}
