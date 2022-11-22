/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLauncherParametersTest {
    @Test
    void GIVEN_testLauncherParameters_WHEN_invokingAvailableMethod_THEN_correctListOfParameter () {
        TestLauncherParameters testLauncherParameters = new TestLauncherParameters();
        assertEquals(9,
                testLauncherParameters.available().size());
    }
}
