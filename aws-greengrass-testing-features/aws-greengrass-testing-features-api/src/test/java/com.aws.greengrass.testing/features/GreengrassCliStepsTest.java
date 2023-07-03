/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.aws.greengrass.testing.features.GreengrassCliSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.NetworkUtils;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.aws.greengrass.testing.resources.AWSResources;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.mockito.Mockito;

import java.nio.file.Paths;
import java.util.Arrays;

public class GreengrassCliStepsTest {
    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_GREENGRASS_INSTALL_ROOT_PATH = "/temp/greengrass";
    private static final String MOCK_DEPLOYMENT_ID = "aaaaaaaa-bbbb-cccc-dddd-111111111111";

    private WaitSteps waitSteps = new WaitSteps(TimeoutMultiplier.builder().multiplier(1).build());
    private TestContext testContext = initializeMockTestContext();

    @Mock
    ComponentPreparationService componentPreparationService;

    @Mock
    AWSResources resources;

    private Platform platform = Mockito.spy(new Platform() {
        @Override
        public Commands commands() {
            return null;
        }

        @Override
        public PlatformFiles files() {
            return null;
        }

        @Override
        public NetworkUtils networkUtils() {
            return null;
        }
    });

    @InjectMocks
    ScenarioContext scenarioContext = Mockito.spy(new ScenarioContext(platform, testContext, resources));

    @InjectMocks
    GreengrassCliSteps greengrassCliSteps = Mockito.spy(new GreengrassCliSteps(platform, testContext,
            componentPreparationService, scenarioContext, waitSteps));


    @Test
    void GIVEN_a_greengrass_cli_step_is_initialized_WHEN_call_verify_cli_installation_THEN_the_cli_path_is_as_expected() {
        PlatformFiles platformFiles = Mockito.mock(PlatformFiles.class);
        Mockito.doReturn(platformFiles).when(platform).files();
        // Verify the absolute path of CLI in the function class doesn't change
        Mockito.doReturn(true).when(platformFiles).exists(testContext.installRoot().resolve("bin").resolve("greengrass-cli"));
        assertDoesNotThrow(() -> greengrassCliSteps.verifyCliInstallation());
    }

    @Test
    void GIVEN_a_local_deployment_happened_WHEN_verify_it_THEN_it_reaches_the_expected_status_after_n_seconds() {
        Mockito.doReturn("SUCCEEDED").when(greengrassCliSteps).getLocalDeploymentStatus();
        assertDoesNotThrow(() -> greengrassCliSteps.verifyLocalDeployment("SUCCEEDED", 30));

        Mockito.doReturn("FAILED").when(greengrassCliSteps).getLocalDeploymentStatus();
        assertDoesNotThrow(() -> greengrassCliSteps.verifyLocalDeployment("FAILED", 30));
    }

    @Test
    void GIVEN_a_valid_deployment_id_WHEN_get_local_deployment_status_THEN_it_returns_a_response_as_expected() {
        Mockito.doReturn(MOCK_DEPLOYMENT_ID).when(scenarioContext).get(Mockito.any());

        Commands mockedCommands = Mockito.mock(Commands.class);
        Mockito.doReturn(mockedCommands).when(platform).commands();

        // build this expectedCommandInput to valid the expected command in the function class doesn't change
        CommandInput expectedCommandInput = CommandInput.builder()
                .line(testContext.installRoot().resolve("bin").resolve("greengrass-cli").toString())
                .addAllArgs(Arrays.asList("deployment", "status", "--deploymentId", MOCK_DEPLOYMENT_ID))
                .build();

        Mockito.doReturn(String.format("%s: IN PROGRESS", MOCK_DEPLOYMENT_ID)).when(mockedCommands)
                .executeToString(expectedCommandInput);

        assertEquals("IN PROGRESS", greengrassCliSteps.getLocalDeploymentStatus());

        Mockito.doReturn(String.format("%s: SUCCEEDED", MOCK_DEPLOYMENT_ID)).when(mockedCommands)
                .executeToString(expectedCommandInput);

        assertEquals("SUCCEEDED", greengrassCliSteps.getLocalDeploymentStatus());
    }

    private TestContext initializeMockTestContext() {
        // build with mock values, all these values are one time mocked value
        return TestContext.builder()
                .installRoot(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH))
                .cleanupContext(CleanupContext.builder().build())
                .coreThingName("mock_core_thing")
                .testId(TestId.builder().id("mock_id").build())
                .testDirectory(Paths.get("mockInstallRoot"))
                .initializationContext(InitializationContext.builder().persistInstalledSoftware(true).build())
                .logLevel("INFO")
                .currentUser("mock_user")
                .coreVersion(MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT)
                .tesRoleName("mock_role_name")
                .testResultsPath(Paths.get("mock_test_result_path"))
                .hsmConfigured(false)
                .build();
    }
}
