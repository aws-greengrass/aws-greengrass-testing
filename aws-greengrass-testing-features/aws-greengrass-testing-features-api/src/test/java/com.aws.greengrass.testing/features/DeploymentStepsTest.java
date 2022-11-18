/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.*;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Commands;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeployment;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeploymentSpec;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;
import software.amazon.awssdk.services.greengrassv2.model.EffectiveDeploymentExecutionStatus;
import software.amazon.awssdk.services.iot.model.GroupNameAndArn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DeploymentStepsTest {
    private static final String MOCK_NUCLEUS_VERSION_IN_TEST_CONTENT = "1.0.0";
    private static final String MOCK_IOT_THING_ARN = "arn:aws:iot:us-west-2:123456789000:thing/MockThing_ea168665-5b14-45fe-b585-0cd5019580fd";
    private static final String MOCK_DEPLOYMENT_ID = "aaaaaaaa-bbbb-cccc-dddd-111111111111";
    private static final String MOCK_THING_GROUP_ARN = "arn:aws:iot:us-west-2:123456789000:thinggroup/mock_name";
    private static final String MOCK_THING_GROUP_NAME = "mock_thing_group_name";
    private static final String MOCK_COMPONENT_A_NAME = "MockComponentA";
    private static final String MOCK_COMPONENT_B_NAME = "MockComponentB";
    private static final String MOCK_COMPONENT_A_VERSION = "1.0.0";
    private static final String MOCK_COMPONENT_B_VERSION = "2.0.0";
    private static final List<List<String>> MOCK_COMPONENTS = new ArrayList<>(
            Arrays.asList(
                    Arrays.asList(MOCK_COMPONENT_A_NAME, "cloud:/greengrass/components/recipes/componentA.yaml"),
                    Arrays.asList(MOCK_COMPONENT_B_NAME, "classpath:/greengrass/components/recipes/componentB.yaml")
            )
    );

    private ObjectMapper mapper = new ObjectMapper();
    private TestContext testContext = initializeMockTestContext();
    private WaitSteps waits = new WaitSteps(TimeoutMultiplier.builder().multiplier(1).build());

    @Mock
    ComponentOverrides overrides;

    @Mock
    ComponentPreparationService componentPreparation;

    @Mock
    Platform platform;

    @Mock
    IotThing iotThing;

    @Mock
    Set<AWSResourceLifecycle> lifecycles;

    @Mock
    CleanupContext cleanupContext;

    @Mock
    GreengrassV2Lifecycle ggv2;

    @Mock
    TestId testId;

    @InjectMocks
    AWSResources resources = Mockito.spy(new AWSResources(lifecycles, cleanupContext, testId));

    @InjectMocks
    ScenarioContext scenarioContext = Mockito.spy(new ScenarioContext(platform, testContext, resources));

    @InjectMocks
    DeploymentSteps deploymentSteps= Mockito.spy(new DeploymentSteps(resources, overrides, testContext,
            componentPreparation, scenarioContext, waits, mapper, platform));

    @Test
    void GIVEN_a_list_of_component_names_with_mixed_type_WHEN_create_deployment_with_this_list_and_then_update_the_configuration_of_one_component_THEN_a_deployment_is_made_first_and_the_deployment_get_updated_successfully() throws JsonProcessingException {
        // Step 1: first create a deployment with multiple components
        createMockDeployment();

        // Step 2: update the deployment made in Step 1 with new configuration
        String configurationUpdate = "{\"MERGE\":" +
                "{\"bucketName\":\"${aws.resources:s3:bucket:bucketName}\"," +
                "\"key\":\"export/streammanager-input.log\"," +
                "\"inputFile\":\"file:${test.context:installRoot}/mock_component-input.log\"," +
                "\"streamName\":\"S3Export-${test.id}\"}}";
        Mockito.doReturn(configurationUpdate).when(scenarioContext).applyInline(Mockito.any());

        // previously deployed deployment
        GreengrassDeploymentSpec deployment = deploymentSteps.deployment;

        assertDoesNotThrow(() -> deploymentSteps.updateDeployment("MockComponentA", configurationUpdate));
    }

    @Test
    void GIVEN_a_list_of_component_names_with_mixed_type_WHEN_create_local_deployment_with_this_list_THEN_a_local_deployment_happens() {
        //input
        List<List<String>> localComponentNames = MOCK_COMPONENTS;

        Mockito.doReturn(new HashMap<String, ComponentDeploymentSpecification>(){{
            put(MOCK_COMPONENT_A_NAME, ComponentDeploymentSpecification.builder()
                    .componentVersion(MOCK_COMPONENT_A_VERSION)
                    .build());
            put(MOCK_COMPONENT_B_VERSION, ComponentDeploymentSpecification.builder()
                    .componentVersion(MOCK_COMPONENT_B_VERSION)
                    .build());
        }}).when(this.deploymentSteps).parseComponentNamesAndPrepare(Mockito.any());

        Commands mockedCommands = Mockito.mock(Commands.class);
        Mockito.doReturn(mockedCommands).when(platform).commands();
        Mockito.doReturn(MOCK_DEPLOYMENT_ID).when(mockedCommands).executeToString(Mockito.any());
        Mockito.doReturn(null).when(scenarioContext).put(Mockito.any(), Mockito.any());

        assertDoesNotThrow(() -> deploymentSteps.createLocalDeployment(localComponentNames));
    }

    @Test
    void GIVEN_a_list_of_component_names_with_mixed_type_got_deployed_first_WHEN_deploy_a_deployment_configuration_to_a_thing_group_THEN_it_works() {
        // Step 1: firstly create a deployment
        createMockDeployment();

        // Step 2: deploy the Greengrass deployment configuration to thing group
        SdkIterable<GroupNameAndArn> mockedThingGroupIterable = Mockito.mock(SdkIterable.class);
        Mockito.doReturn(mockedThingGroupIterable).when(deploymentSteps).getThingGroupIterable(Mockito.any());


        Mockito.doReturn(Stream.of(GroupNameAndArn.builder()
                        .groupArn(MOCK_THING_GROUP_ARN)
                        .groupName(MOCK_THING_GROUP_NAME)
                        .build()))
                .when(mockedThingGroupIterable).stream();
        Mockito.doReturn(deploymentSteps.deployment).when(resources).create(Mockito.any());

        GreengrassDeploymentSpec originalDeployment = deploymentSteps.deployment;
        GreengrassDeploymentSpec updatedDeployment = originalDeployment
                .withResource(GreengrassDeployment.builder().deploymentId(MOCK_DEPLOYMENT_ID).build());
        Mockito.doReturn(updatedDeployment).when(resources).create(Mockito.any());
        deploymentSteps.startDeploymentForThingGroup(MOCK_THING_GROUP_NAME);
    }

    @Test
    void GIVEN_a_deployment_happened_WHEN_check_if_deployment_reaches_a_specific_status_after_a_period_of_time_THEN_it_works()
            throws InterruptedException {
        // Step 1: firstly create a deployment
        createMockDeployment();

        // Step 2: check status
        Mockito.doReturn(Optional.ofNullable(EffectiveDeploymentExecutionStatus.COMPLETED))
                .when(deploymentSteps).effectivelyDeploymentStatus();
        deploymentSteps.deploymentSucceeds("COMPLETED", 300, "seconds");

        Mockito.doReturn(Optional.ofNullable(EffectiveDeploymentExecutionStatus.CANCELED))
                .when(deploymentSteps).effectivelyDeploymentStatus();
        deploymentSteps.deploymentSucceeds("CANCELED", 300, "seconds");

        Mockito.doReturn(Optional.ofNullable(EffectiveDeploymentExecutionStatus.FAILED))
                .when(deploymentSteps).effectivelyDeploymentStatus();
        deploymentSteps.deploymentSucceeds("FAILED", 300, "seconds");

        Mockito.doReturn(Optional.ofNullable(EffectiveDeploymentExecutionStatus.REJECTED))
                .when(deploymentSteps).effectivelyDeploymentStatus();
        deploymentSteps.deploymentSucceeds("REJECTED", 1, "minutes");

        Mockito.doReturn(Optional.ofNullable(EffectiveDeploymentExecutionStatus.TIMED_OUT))
                .when(deploymentSteps).effectivelyDeploymentStatus();
        deploymentSteps.deploymentSucceeds("TIMED_OUT", 1, "minutes");
    }

    @Test
    void GIVEN_a_target_thing_arn_WHEN_an_empty_deployment_happens_THEN_it_clean_up_component() {
        Mockito.doNothing().when(deploymentSteps).checkADeploymentReachesCompleted(Mockito.any(), Mockito.any());
        Mockito.doReturn(ggv2).when(resources).lifecycle(GreengrassV2Lifecycle.class);

        GreengrassDeploymentSpec mockEmptyDeployment = GreengrassDeploymentSpec.builder()
                .resource(GreengrassDeployment.builder().deploymentId(MOCK_DEPLOYMENT_ID).build())
                .deploymentName("EmptyDeployment")
                .thingArn(MOCK_IOT_THING_ARN)
                .build();

        Mockito.doReturn(mockEmptyDeployment).when(resources).create(Mockito.any());
        Mockito.doNothing().when(deploymentSteps).checkADeploymentReachesCompleted(Mockito.any(),
                Mockito.any());

        assertDoesNotThrow(() -> deploymentSteps.emptyDeployment(MOCK_IOT_THING_ARN));
    }



    private void createMockDeployment() {
        List<List<String>> componentNames = MOCK_COMPONENTS;

        Mockito.doReturn(iotThing).when(deploymentSteps).getIotThing();
        Mockito.doReturn(MOCK_IOT_THING_ARN).when(iotThing).thingArn();
        Mockito.doReturn(new HashMap<String, ComponentDeploymentSpecification>(){{
            put(MOCK_COMPONENT_A_NAME, ComponentDeploymentSpecification.builder()
                    .componentVersion(MOCK_COMPONENT_A_VERSION)
                    .build());
            put(MOCK_COMPONENT_B_NAME, ComponentDeploymentSpecification.builder()
                    .componentVersion(MOCK_COMPONENT_B_VERSION)
                    .build());
        }}).when(this.deploymentSteps).parseComponentNamesAndPrepare(Mockito.any());

        deploymentSteps.createDeployment(componentNames);
    }


    private TestContext initializeMockTestContext() {
        // build with mock values, all these values are one time mocked value
        return TestContext.builder()
                .installRoot(Paths.get("mockInstallRoot"))
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
