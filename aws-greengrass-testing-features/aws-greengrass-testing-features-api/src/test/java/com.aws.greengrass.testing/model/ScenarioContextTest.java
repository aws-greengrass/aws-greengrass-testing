/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.NetworkUtils;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformFiles;
import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeployment;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeploymentSpec;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.Set;

public class ScenarioContextTest {
    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_GREENGRASS_INSTALL_ROOT_PATH = "/temp/greengrass";
    private static final String SCENARIO_ONE_KEY = "aws.resources:greengrass:Deployment:builder";
    private static final String SCENARIO_TWO_KEY = "test.context:installRoot";
    private static final String SCENARIO_THREE_KEY = "localDeployment";
    private static final String SCENARIO_THREE_VALUE = "mock_value";
    private static final String EXPECTED_PASCAL_CASE_SCENARIO_THREE_VALUE = "Mock_value";

    private Commands mockCommands = Mockito.mock(Commands.class);

    private PlatformFiles mockPlatformFiles = Mockito.mock(PlatformFiles.class);

    private NetworkUtils mockNetworkUtils = Mockito.mock(NetworkUtils.class);

    private Platform platform = new Platform() {
        @Override
        public Commands commands() {
            return mockCommands;
        }

        @Override
        public PlatformFiles files() {
            return mockPlatformFiles;
        }

        @Override
        public NetworkUtils networkUtils() {
            return mockNetworkUtils;
        }
    };

    private TestContext testContext = initializeMockTestContext();

    @Mock
    Set<AWSResourceLifecycle> lifecycles;

    @Mock
    CleanupContext cleanupContext;

    @Mock
    TestId testId;

    @InjectMocks
    AWSResources resources = Mockito.spy(new AWSResources(lifecycles, cleanupContext, testId));

    @InjectMocks
    ScenarioContext scenarioContext = Mockito.spy(new ScenarioContext(platform, testContext, resources));


    @Test
    void GIVEN_a_tracking_key_WHEN_get_method_is_called_THEN_it_returns_expected_context_content() {
        // Scenario 1
        Mockito.doReturn(Stream.of(GreengrassDeployment.class)).when(resources).trackingSpecs(GreengrassDeploymentSpec.class);
        assertDoesNotThrow(() -> scenarioContext.get(SCENARIO_ONE_KEY));

        // Scenario 2
        Mockito.doReturn(MOCK_GREENGRASS_INSTALL_ROOT_PATH).when(mockPlatformFiles).format(testContext.installRoot());
        assertEquals(MOCK_GREENGRASS_INSTALL_ROOT_PATH, scenarioContext.get(SCENARIO_TWO_KEY));

        // Scenario 3
        assertEquals(null, scenarioContext.get(SCENARIO_THREE_KEY));
    }

    @Test
    void GIVEN_key_and_value_as_inputs_WHEN_put_method_is_called_THEN_context_has_these_two_inputs_context() {
        ScenarioContext returnedScenarioContext = scenarioContext.put(SCENARIO_THREE_KEY, SCENARIO_THREE_VALUE);
        assertEquals(returnedScenarioContext.get(SCENARIO_THREE_KEY), SCENARIO_THREE_VALUE);
    }

    @Test
    void GIVEN_a_name_as_input_WHEN_pascal_case_is_called_THEN_it_returns_pascal_case_name() {
        assertEquals(EXPECTED_PASCAL_CASE_SCENARIO_THREE_VALUE, scenarioContext.pascalCase(SCENARIO_THREE_VALUE));
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
