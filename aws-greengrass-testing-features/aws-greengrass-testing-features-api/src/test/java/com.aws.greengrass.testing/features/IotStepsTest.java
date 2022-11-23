/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;

public class IotStepsTest {
    private static final String MOCK_POLICY_NAME = "mock_policy_name";
    private static final String MOCK_POLICY_DOCUMENT = "mock_policy_document";

    @Mock
    TestId testId;

    @Mock
    AWSResources resources;

    private ObjectMapper  objectMapper = new ObjectMapper(new YAMLFactory());

    @InjectMocks
    IotSteps iotSteps = Mockito.spy(new IotSteps(testId, resources, objectMapper));

    @Test
    void GIVEN_a_test_case_wants_to_create_default_IoT_policy_WHEN_create_default_policy_method_is_called_without_inputs_THEN_it_returns_a_valid_iot_policy_spec_as_expected() throws IOException {
        Mockito.doReturn(IotPolicySpec.builder()
                .policyName(MOCK_POLICY_NAME)
                .policyDocument(MOCK_POLICY_DOCUMENT)
                .build()).when(iotSteps).createPolicy(Mockito.anyString(), Mockito.nullable(String.class));
        assertDoesNotThrow(() -> iotSteps.createDefaultPolicy());
        Mockito.verify(iotSteps, Mockito.times(1)).createDefaultPolicy(Mockito.nullable(String.class));
    }
}
