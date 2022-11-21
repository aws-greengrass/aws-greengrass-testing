/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.FeatureParameters;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.aws.greengrass.testing.api.util.FileUtils;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.aws.greengrass.testing.resources.iam.IamRole;
import com.aws.greengrass.testing.resources.iot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;


import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iot.model.KeyPair;
import software.amazon.awssdk.utils.IoUtils;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

public class RegistrationStepsTest {
    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_GREENGRASS_INSTALL_ROOT_PATH = "/temp/greengrass";
    private static final String MOCK_CONFIG_NAME = "mock_config_name";
    private static final String MOCK_THING_NAME = "mock_thing_name";
    private static final String MOCK_THING_GROUP_NAME = "mock_thing_group_name";
    private static final String MOCK_ROLE_ALIAS = "mock_role_alias";
    private static final String MOCK_ROLE_ALIAS_ARN = "mock_role_alias_arn";
    private static final String MOCK_ROLE_ALIAS_IAM_ROLE = "mock_role_alias_iam_role";
    private static final String MOCK_IAM_ROLE_ARN = "mock_iam_role_arn";
    private static final String MOCK_IAM_ROLE_NAME = "mock_iam_role_name";
    private static final String MOCK_ROLE_ALIAS_NAME = "mock_role_alias_name";
    private static final String MOCK_CSR_PATH = "/path/to/csr";
    private static final String MOCK_IOT_DATA_ENDPOINT = "mock_iot_data_endpoint";
    private static final String MOCK_IOT_CRED_ENDPOINT = "mock_iot_cred_endpoint";
    private static final String MOCK_PRIVATE_KEY = "mock_private_key";
    private static final String MOCK_CERTIFICATE_PEM= "mock_certificate_pem";
    private static final String MOCK_CERTIFICATE_ARN = "mock_certificate_arn";
    private static final String MOCK_CERTIFICATE_ID = "mock_certificate_id";
    private static final String MOCK_THING_ARN = "mock_thing_arn";
    private static final String MOCK_THING_ID = "mock_thing_id";
    private static final String MOCK_ROOT_CA = "mock_root_ca";
    private static final String MOCK_INSTALL_ROOT = "mockInstallRoot";
    private static final int MOCK_CONNECTION_PORT = 9999;

    private Commands mockCommands = Mockito.mock(Commands.class);
    private PlatformFiles mockPlatformFiles = Mockito.mock(PlatformFiles.class);

    private Platform platform = new Platform() {
        @Override
        public Commands commands() {
            return mockCommands;
        }

        @Override
        public PlatformFiles files() {
            return mockPlatformFiles;
        }
    };

    private AWSResourcesContext resourcesContext = AWSResourcesContext.builder()
            .region(Region.US_EAST_1).envStage("prod").build();

    private RegistrationContext registrationContext = RegistrationContext.builder()
            .connectionPort(MOCK_CONNECTION_PORT).rootCA(MOCK_ROOT_CA).build();

    @Mock
    IamSteps iamSteps;

    @Mock
    IotSteps iotSteps;

    @Mock
    IamLifecycle iamLifecycle;

    @Mock
    FileSteps fileSteps;

    private AWSResources resources = Mockito.mock(AWSResources.class);
    private ParameterValues parameterValues =  Mockito.mock(ParameterValues.class);
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

    @Test
    void GIVEN_config_files_WHEN_setup_config_method_is_called_THEN_the_expected_config_file_has_been_created_with_expected_value() throws IOException {
        Path basicConfigFilePath = Paths.get(System.getProperty("user.dir"),"src", "test", "resources",
                "nucleus.configs", "basic_config.yaml");

        String config = IoUtils.toUtf8String(new FileInputStream(basicConfigFilePath.toString()));
        IotThing iotThing = IotThing.builder()
                .thingName(MOCK_THING_NAME)
                .certificate(IotCertificate.builder()
                        .keyPair(KeyPair.builder().privateKey(MOCK_PRIVATE_KEY).build())
                        .certificatePem(MOCK_CERTIFICATE_PEM)
                        .certificateArn(MOCK_CERTIFICATE_ARN)
                        .certificateId(MOCK_CERTIFICATE_ID)
                        .build())
                .thingArn(MOCK_THING_ARN)
                .thingId(MOCK_THING_ID)
                .build();

        IotRoleAliasSpec iotRoleAliasSpec = IotRoleAliasSpec.builder()
                .resource(IotRoleAlias.builder()
                        .roleAlias(MOCK_ROLE_ALIAS)
                        .roleAliasArn(MOCK_ROLE_ALIAS_ARN)
                        .build())
                .name(MOCK_ROLE_ALIAS_NAME)
                .iamRole(IamRole.builder()
                        .roleArn(MOCK_IAM_ROLE_ARN)
                        .roleName(MOCK_IAM_ROLE_NAME)
                        .build())
                .build();

        IotLifecycle mockIotLifecycle = Mockito.mock(IotLifecycle.class);
        Mockito.doReturn(mockIotLifecycle).when(resources).lifecycle(IotLifecycle.class);
        Mockito.doReturn(MOCK_IOT_DATA_ENDPOINT).when(mockIotLifecycle).dataEndpoint();
        Mockito.doReturn(MOCK_IOT_CRED_ENDPOINT).when(mockIotLifecycle).credentialsEndpoint();
        Mockito.doNothing().when(mockPlatformFiles).makeDirectories(Mockito.any());
        Mockito.doNothing().when(mockPlatformFiles).copyTo(Mockito.any(), Mockito.any());

        registrationSteps.setupConfig(iotThing, iotRoleAliasSpec, config, new HashMap<>());

        // all the following files should exist if setupconfig runs as expected
        Path expectedOutputConfigPath = Paths.get(System.getProperty("user.dir"),MOCK_INSTALL_ROOT, "config", "config.yaml");
        Path expectedOutputPrivateKeyPath = Paths.get(System.getProperty("user.dir"),MOCK_INSTALL_ROOT, "privKey.key");
        Path expectedOutputRootCAPath = Paths.get(System.getProperty("user.dir"),MOCK_INSTALL_ROOT, "rootCA.pem");
        Path expectedOutputThingCertPath = Paths.get(System.getProperty("user.dir"),MOCK_INSTALL_ROOT, "thingCert.crt");

        assertTrue(Files.exists(expectedOutputConfigPath));
        assertTrue(Files.exists(expectedOutputPrivateKeyPath));
        assertTrue(Files.exists(expectedOutputRootCAPath));
        assertTrue(Files.exists(expectedOutputThingCertPath));

        // do clean up
        FileUtils.recursivelyDelete(Paths.get(System.getProperty("user.dir"), MOCK_INSTALL_ROOT));
    }

    private TestContext initializeMockTestContext() {
        // build with mock values, all these values are one time mocked value
        return TestContext.builder()
                .installRoot(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH))
                .cleanupContext(CleanupContext.builder().build())
                .coreThingName("mock_core_thing")
                .testId(TestId.builder().id("mock_id").build())
                .testDirectory(Paths.get(MOCK_INSTALL_ROOT))
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
