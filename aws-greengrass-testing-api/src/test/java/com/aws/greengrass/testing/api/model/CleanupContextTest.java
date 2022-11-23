/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CleanupContextTest {
    @Test
    void GIVEN_notPersistAnyMode_WHEN_generateCleanUpContext_THEN_returnFalseForAllPersistChecks() {
        Collection<PersistMode> collection = new ArrayList<>();
        CleanupContext cleanupContext = CleanupContext.fromModes(collection);
        assertFalse(cleanupContext.persistAWSResources());
        assertFalse(cleanupContext.persistInstalledSoftware());
        assertFalse(cleanupContext.persistGeneratedFiles());
    }

    @Test
    void GIVEN_persistAWSResources_WHEN_generateCleanUpContext_THEN_returnTrueForPersistAWSResources() {
        PersistMode persistMode = PersistMode.AWS_RESOURCES;
        Collection<PersistMode> collection = new ArrayList<>();
        collection.add(persistMode);
        CleanupContext cleanupContext = CleanupContext.fromModes(collection);
        assertTrue(cleanupContext.persistAWSResources());
        assertFalse(cleanupContext.persistInstalledSoftware());
        assertFalse(cleanupContext.persistGeneratedFiles());
    }

    @Test
    void GIVEN_persistInstalledSoftware_WHEN_generateCleanUpContext_THEN_returnTrueForPersistInstalledSoftware() {
        PersistMode persistMode = PersistMode.INSTALLED_SOFTWARE;
        Collection<PersistMode> collection = new ArrayList<>();
        collection.add(persistMode);
        CleanupContext cleanupContext = CleanupContext.fromModes(collection);
        assertFalse(cleanupContext.persistAWSResources());
        assertTrue(cleanupContext.persistInstalledSoftware());
        assertFalse(cleanupContext.persistGeneratedFiles());
    }

    @Test
    void GIVEN_persistGeneratedFiles_WHEN_generateCleanUpContext_THEN_returnTrueForPersistGeneratedFiles() {
        PersistMode persistMode = PersistMode.GENERATED_FILES;
        Collection<PersistMode> collection = new ArrayList<>();
        collection.add(persistMode);
        CleanupContext cleanupContext = CleanupContext.fromModes(collection);
        assertFalse(cleanupContext.persistAWSResources());
        assertFalse(cleanupContext.persistInstalledSoftware());
        assertTrue(cleanupContext.persistGeneratedFiles());
    }

    @Test
    void GIVEN_persistAll_WHEN_generateCleanUpContext_THEN_returnTrueForAllPersistChecks() {
        PersistMode persistModeAWSResources = PersistMode.AWS_RESOURCES;
        PersistMode persistModeInstalledSoftware = PersistMode.INSTALLED_SOFTWARE;
        PersistMode persistModeGeneratedFiles = PersistMode.GENERATED_FILES;
        Collection<PersistMode> collection = new ArrayList<>();
        collection.add(persistModeAWSResources);
        collection.add(persistModeInstalledSoftware);
        collection.add(persistModeGeneratedFiles);
        CleanupContext cleanupContext = CleanupContext.fromModes(collection);
        assertTrue(cleanupContext.persistAWSResources());
        assertTrue(cleanupContext.persistInstalledSoftware());
        assertTrue(cleanupContext.persistGeneratedFiles());
    }
}
