/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PersistModeTest {
    @Test
    void GIVEN_invalidConfigValue_WHEN_pullingPersistModeFromConfigString_THEN_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> PersistMode.fromConfig("test"));
    }

    @Test
    void GIVEN_validConfigValue_WHEN_pullingPersistModeFromConfigString_THEN_returnValidPersistMode() {
        PersistMode persistMode = PersistMode.fromConfig("AWS_RESOURCES");
        assertEquals("aws.resources", persistMode.toString());

        persistMode = PersistMode.fromConfig("INSTALLED_SOFTWARE");
        assertEquals("installed.software", persistMode.toString());

        persistMode = PersistMode.fromConfig("GENERATED_FILES");
        assertEquals("generated.files", persistMode.toString());
    }

    @Test
    void GIVEN_inValidValue_WHEN_validPersistMode_THEN_returnFalse() {
        assertFalse(PersistMode.validPersistMode(""));
    }

    @Test
    void GIVEN_validValue_WHEN_validPersistMode_THEN_returnTrue() {
        assertTrue(PersistMode.validPersistMode("AWS_RESOURCES"));
        assertTrue(PersistMode.validPersistMode("INSTALLED_SOFTWARE"));
        assertTrue(PersistMode.validPersistMode("GENERATED_FILES"));
    }
}
