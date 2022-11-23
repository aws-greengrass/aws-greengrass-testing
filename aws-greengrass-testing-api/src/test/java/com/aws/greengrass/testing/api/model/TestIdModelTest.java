/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIdModelTest {
    private String mockTestId = "mock_test_id";
    private TestIdModel testIdModel = new TestIdModel() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String id() {
            return mockTestId;
        }
    };

    @Test
    void GIVEN_validTestId_WHEN_addPrefixToId_THEN_returnPrefixedId() {
        assertEquals("test-" + mockTestId, testIdModel.prefixedId());
    }

    @Test
    void GIVEN_validTestId_WHEN_invokingidFor_THEN_returnFormattedId() {
        assertEquals("test-" + mockTestId + "-type", testIdModel.idFor("type"));
    }
}
