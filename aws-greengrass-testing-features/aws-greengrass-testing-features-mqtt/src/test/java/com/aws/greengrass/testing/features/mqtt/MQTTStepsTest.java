/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.testing.features.mqtt;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.aws.greengrass.testing.resources.iot.IotCertificate;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.services.iot.model.KeyPair;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MQTTStepsTest {
    @Mock
    Set<AWSResourceLifecycle> lifecycles;

    @Mock
    CleanupContext cleanupContext;

    @Mock
    TestId testId;

    TestContext testContext = initializeMockTestContext();

    @Mock
    Platform platform;

    RegistrationContext registrationContext = RegistrationContext.builder()
            .connectionPort(9999).rootCA("mock_root_CA").build();

    @Mock
    WaitSteps waits;

    @InjectMocks
    AWSResources resources = Mockito.spy(new AWSResources(lifecycles, cleanupContext, testId));

    @InjectMocks
    ScenarioContext scenarioContext = Mockito.spy(new ScenarioContext(platform, testContext, resources));

    @InjectMocks
    IotSteps iotSteps = Mockito.spy(new IotSteps(testId, resources, new ObjectMapper(new YAMLFactory())));

    @InjectMocks
    MQTTSteps mqttSteps = Mockito.spy(new MQTTSteps(resources, testContext, scenarioContext, registrationContext, waits, iotSteps));

    @Test
    void GIVEN_a_mqtt_client_tries_to_connect_WHEN_connect_method_is_called_THEN_it_works_as_expected()
            throws ExecutionException, InterruptedException {
        Mockito.doReturn(IotThingSpec.builder()
                .thingName("mock_thing_name")
                .resource(IotThing.builder()
                        .certificate(IotCertificate.builder()
                                .keyPair(KeyPair.builder()
                                        .privateKey("-----BEGIN PRIVATE KEY-----\ncertificate content\n-----END PRIVATE KEY-----\n")
                                        .build())
                                .certificatePem("-----BEGIN CERTIFICATE-----\ncertificate content\n-----END CERTIFICATE-----\n")
                                .certificateArn("mock_certificate_arn")
                                .certificateId("mock_certificate_id")
                                .build())
                        .thingId("mock_thing_id")
                        .thingArn("mock_thing_arn")
                        .thingName("mock_thing_name")
                        .build())
                .build()).when(resources).create(Mockito.any());

        Mockito.doReturn(new IotLifecycle()).when(resources).lifecycle(IotLifecycle.class);
        Mockito.doReturn(IotPolicySpec.builder()
                        .policyDocument("mock_doc")
                        .policyName("mock_policy_name")
                        .build())
                .when(iotSteps).createDefaultPolicy(Mockito.any());

        Mockito.doNothing().when(mqttSteps).establishConnection(Mockito.any(), Mockito.any());
        assertDoesNotThrow(() -> mqttSteps.connect());
    }

    @Test
    void GIVEN_a_mqtt_client_tries_to_publish_messages_WHEN_publish_messages_through_a_topic_THEN_it_works ()
            throws ExecutionException, InterruptedException {
        Mockito.doNothing().when(mqttSteps).connect();
        Mockito.doReturn(false).when(mqttSteps).isConnected();

        List<List<String>> topicTuples = new ArrayList<>(
                Arrays.asList(Arrays.asList("mock_topic_a", "mockData"), Arrays.asList("mock_topic_b", "mockData"))
        );

        Mockito.doReturn("mock_real_topic").when(scenarioContext).get(Mockito.any());


        MqttClientConnection mockMqttClientConnection = Mockito.mock(MqttClientConnection.class);
        Mockito.doReturn(mockMqttClientConnection).when(mqttSteps).getConnection();

        CompletableFuture<Integer> mockCompletableFuture = Mockito.mock(CompletableFuture.class);
        Mockito.doReturn(mockCompletableFuture).when(mockMqttClientConnection).publish(Mockito.any());
        Mockito.doReturn(0).when(mockCompletableFuture).get();
        assertDoesNotThrow(() -> mqttSteps.publishMessages(topicTuples));
    }

    @Test
    void GIVEN_a_mqtt_client_tries_to_subscribe_messages_WHEN_subscribe_to_topics_THEN_it_works()
            throws ExecutionException, InterruptedException {
        Mockito.doNothing().when(mqttSteps).connect();
        Mockito.doReturn(false).when(mqttSteps).isConnected();

        CompletableFuture<Integer> mockCompletableFuture = Mockito.mock(CompletableFuture.class);
        Mockito.doReturn(mockCompletableFuture).when(mqttSteps).getSubscription(Mockito.any(), Mockito.any());
        Mockito.doReturn(0).when(mockCompletableFuture).get();

        assertDoesNotThrow(() -> mqttSteps.subscribeToTopics(Arrays.asList("mock_topic_a", "mock_topic_b")));
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
                .coreVersion("2.1.0")
                .tesRoleName("mock_role_name")
                .testResultsPath(Paths.get("mock_test_result_path"))
                .hsmConfigured(false)
                .build();
    }

}
