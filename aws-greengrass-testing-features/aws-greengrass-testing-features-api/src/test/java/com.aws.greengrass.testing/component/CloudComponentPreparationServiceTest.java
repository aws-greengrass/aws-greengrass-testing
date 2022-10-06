/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrassv2.model.Component;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
public class CloudComponentPreparationServiceTest {
    Region currentRegion = Region.AF_SOUTH_1;

    @Mock
    GreengrassV2Lifecycle ggv2;

    @Mock
    GreengrassContext ggContext;

    @Mock
    ParameterValues parameterValues;

    @Mock
    TestContext testContext;

    @InjectMocks
    CloudComponentPreparationService componentPreparation;

    @BeforeEach
    public void setup() {
        componentPreparation = Mockito.spy(new CloudComponentPreparationService(ggv2, currentRegion, ggContext, parameterValues, testContext));
    }

    @Test
    void GIVEN_component_override_name_Version_WHEN_it_is_cloud_component_THEN_return_an_expected_componentOverrideNameVersion() {
        // Kernel
        Mockito.when(ggContext.version())
                .thenReturn("mock_version");
        Mockito.doReturn(Optional.ofNullable(Component.builder().componentName("NUCLEUS_VERSION").arn("mock_nucleus_arn").build()))
                .when(componentPreparation).pinpointComponent(Mockito.any());

        ComponentOverrideNameVersion overrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of("cloud", "NUCLEUS_VERSION"))
                .build();

        Optional<ComponentOverrideNameVersion> res = componentPreparation.prepare(overrideNameVersion);
        Optional<ComponentOverrideNameVersion> convertedRes = Optional.ofNullable(componentPreparation.convert(overrideNameVersion, ggContext.version()));

        assertEquals(res.get().name(), convertedRes.get().name());
        assertEquals(res.get().version().value(), convertedRes.get().version().value());
        assertEquals(res.get().version().type(), convertedRes.get().version().type());

        // Cli
        Mockito.doReturn(Optional.ofNullable("GG_CLI_VERSION")).when(parameterValues).getString(Mockito.any());
        Mockito.doReturn(Optional.ofNullable(Component.builder().componentName("GG_CLI_VERSION").arn("mock_cli_arn").build()))
                .when(componentPreparation).pinpointComponent(Mockito.any());

        overrideNameVersion = ComponentOverrideNameVersion.builder()
                .name("mock_component_name")
                .version(ComponentOverrideVersion.of("cloud", "GG_CLI_VERSION"))
                .build();

        assertEquals(componentPreparation.prepare(overrideNameVersion).get().version().value(), "GG_CLI_VERSION");
    }

}
