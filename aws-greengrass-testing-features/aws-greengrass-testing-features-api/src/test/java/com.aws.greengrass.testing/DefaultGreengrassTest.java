/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.aws.greengrass.testing.features.FileSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.aws.greengrass.testing.resources.AWSResources;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.mockito.Mock;
import org.mockito.Mockito;
import software.amazon.awssdk.regions.GeneratedRegionMetadataProvider;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class DefaultGreengrassTest {

    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_GREENGRASS_INSTALL_ROOT_PATH = "/temp/greengrass";
    private static final int MOCK_NUCLEUS_PROCESS_ID = 1111;
    private static final String MOCK_AWS_REGION = "us-west-2";
    private static final String MOCK_ENV_STAGE = "prod";

    @Mock
    AWSResourcesContext resourcesContext;

    @Mock
    AWSResources resources;

    private TestContext testContext = initializeMockTestContext();

    private GreengrassContext greengrassContext = GreengrassContext.builder()
            .cleanupContext(CleanupContext.builder().build())
            .tempDirectory(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH))
            .build();

    private WaitSteps waits = new WaitSteps(TimeoutMultiplier.builder().multiplier(1).build());

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

    @InjectMocks
    ScenarioContext scenarioContext = Mockito.spy(new ScenarioContext(platform, testContext, resources));

    @InjectMocks
    FileSteps fileSteps = Mockito.spy(new FileSteps(platform, testContext, scenarioContext, waits));

    @InjectMocks
    DefaultGreengrass greengrass = Mockito.spy(new DefaultGreengrass(platform, resourcesContext, greengrassContext,
            testContext, waits, fileSteps));


    @Test
    void GIVEN_a_test_that_installs_greengrass_WHEN_install_method_is_called_THEN_greengrass_got_installed_successfully() {
        Mockito.doReturn(false).when(greengrass).isRegistered();

        Mockito.doNothing().when(mockPlatformFiles).copyTo(Mockito.any(), Mockito.any());
        Mockito.doReturn(MOCK_AWS_REGION).when(greengrass).getAWSRegion();
        Mockito.doReturn(MOCK_ENV_STAGE).when(greengrass).getEnvStage();
        Mockito.doNothing().when(mockCommands).installNucleus(Mockito.any());
        assertDoesNotThrow(() -> greengrass.install());
    }

    @Test
    void GIVEN_a_test_that_has_installed_greengrass_WHEN_start_method_is_called_THEN_greengrass_starts_with_a_process_id() {
        startGreengrass();
        assertEquals(MOCK_NUCLEUS_PROCESS_ID, greengrass.getGreengrassProcess());
    }

    @Test
    void GIVEN_a_greengrass_is_running_with_a_pid_WHEN_stop_method_is_called_THEN_all_processes_started_by_greengrass_are_killed() {
        startGreengrass();

        int runningGreengrassProcess = greengrass.getGreengrassProcess();
        Mockito.doNothing().when(mockCommands).killAll(runningGreengrassProcess);
        Mockito.doReturn(new ArrayList<>(Arrays.asList(1))).when(mockCommands).findDescendants(runningGreengrassProcess);

        assertDoesNotThrow(() -> greengrass.stop());
        assertEquals(0, greengrass.getGreengrassProcess());
    }

    private void startGreengrass() {
        Mockito.doReturn(false).when(greengrass).isRunning();
        Mockito.doReturn(MOCK_NUCLEUS_PROCESS_ID).when(mockCommands).startNucleus(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH));
        greengrass.start();


    }

    private TestContext initializeMockTestContext() {
        // build with mock values, all these values are one time mocked value
        return TestContext.builder()
                .installRoot(Paths.get(MOCK_GREENGRASS_INSTALL_ROOT_PATH))
                .cleanupContext(CleanupContext.builder().persistInstalledSoftware(false).build())
                .coreThingName("mock_core_thing")
                .testId(TestId.builder().id("mock_id").build())
                .testDirectory(Paths.get("mockInstallRoot"))
                .initializationContext(InitializationContext.builder().persistInstalledSoftware(true).build())
                .logLevel("INFO")
                .currentUser("mock_user")
                .coreVersion(MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT)
                .tesRoleName("mock_role_name")
                .trustedPluginsPaths(new ArrayList<>(Arrays.asList("/mock_directory_path/plugin_path")))
                .testResultsPath(Paths.get("mock_test_result_path"))
                .hsmConfigured(false)
                .build();
    }


}
