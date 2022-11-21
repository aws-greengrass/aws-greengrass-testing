/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.ParameterValue;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.FeatureParameters;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;

public class RegistrationStepsTest {
    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_GREENGRASS_INSTALL_ROOT_PATH = "/temp/greengrass";
    private static final String MOCK_CONFIG_NAME = "mock_config_name";
    private static final String MOCK_THING_NAME = "mock_thing_name";
    private static final String MOCK_THING_GROUP_NAME = "mock_thing_group_name";
    private static final String MOCK_CSR_PATH = "/path/to/csr";

    @Mock
    Platform platform;

    @Mock
    IamSteps iamSteps;

    @Mock
    IotSteps iotSteps;

    @Mock
    RegistrationContext registrationContext;

    @Mock
    AWSResourcesContext resourcesContext;

    @Mock
    IamLifecycle iamLifecycle;

    AWSResources resources = Mockito.mock(AWSResources.class);

    ParameterValues parameterValues = Mockito.mock(ParameterValues.class);

    @Mock
    FileSteps fileSteps;

    private TestContext testContext = initializeMockTestContext();
    private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @InjectMocks
    RegistrationSteps registrationSteps = Mockito.spy(new RegistrationSteps(platform, resources, iamSteps, iotSteps,
            testContext, registrationContext, resourcesContext, iamLifecycle, parameterValues, fileSteps, objectMapper));

    @Test
    void GIVEN_only_the_config_name_WHEN_register_as_thing_is_invocated_THEN_it_works_as_expected() throws IOException {
        Mockito.doNothing().when(registrationSteps).registerAsThing(Mockito.any(), Mockito.any());
        registrationSteps.registerAsThing(MOCK_CONFIG_NAME);
        Mockito.verify(registrationSteps, Mockito.times(1)).registerAsThing(Mockito.any(), Mockito.any());
    }

    @Test
    void GIVEN_no_inputs_WHEN_register_as_thing_is_invocated_THEN_it_works_as_expected() throws IOException {
        Mockito.doNothing().when(registrationSteps).registerAsThing(null);
        registrationSteps.registerAsThing();
        Mockito.verify(registrationSteps, Mockito.times(1)).registerAsThing(null);
        Mockito.verify(registrationSteps, Mockito.times(0)).checkHSMConfigForPreInstalled();
    }

    @Test
    void GIVEN_config_name_and_thing_group_name_as_inputs_WHEN_register_as_thing_is_invocated_THEN_it_works_as_expecte () throws IOException {
        Mockito.doReturn(Optional.of(MOCK_CSR_PATH)).when(parameterValues).getString(FeatureParameters.CSR_PATH);
        Mockito.doReturn(IotThingSpec.builder().thingName(MOCK_THING_NAME).resource(null).roleAliasSpec(null).build())
                .when(registrationSteps).getThingSpec(Mockito.any(),Mockito.any(),Mockito.any());
        Mockito.doNothing().when(registrationSteps).setupConfigWithConfigFile(Mockito.any(), Mockito.any());

        registrationSteps.registerAsThing(null, MOCK_THING_GROUP_NAME);
        Mockito.verify(registrationSteps, Mockito.times(1)).setupConfigWithConfigFile(Mockito.any(), Mockito.any());
    }




    private TestContext initializeMockTestContext() {
        // build with mock values, all these values are one time mocked value
        return TestContext.builder()
                .installRoot(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH))
                .cleanupContext(CleanupContext.builder().build())
                .coreThingName("mock_core_thing")
                .testId(TestId.builder().id("mock_id").build())
                .testDirectory(Paths.get("mockInstallRoot"))
                .initializationContext(InitializationContext.builder().persistInstalledSoftware(false).build())
                .logLevel("INFO")
                .currentUser("mock_user")
                .coreVersion(MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT)
                .tesRoleName("")
                .testResultsPath(Paths.get("mock_test_result_path"))
                .hsmConfigured(false)
                .build();
    }
}
