/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

public enum PersistMode implements Function<PersistenceBuilder, PersistenceBuilder> {
    AWS_RESOURCES(PersistenceBuilder::persistAWSResources),
    INSTALLED_SOFTWARE(PersistenceBuilder::persistInstalledSoftware),
    GENERATED_FILES(PersistenceBuilder::persistGeneratedFiles);

    BiFunction<PersistenceBuilder, Boolean, PersistenceBuilder> applicator;

    PersistMode(BiFunction<PersistenceBuilder, Boolean, PersistenceBuilder> applicator) {
        this.applicator = applicator;
    }

    @Override
    public String toString() {
        return name().toLowerCase().replace('_', '.');
    }

    /**
     * Pull a {@link PersistMode} from a string value like an input parameter from {@link System} getProperty.
     *
     * @param value String value to convert to {@link PersistMode}
     * @return
     */
    public static PersistMode fromConfig(String value) {
        for (PersistMode mode : values()) {
            if (mode.equals(PersistMode.valueOf(value.replace('.', '_').toUpperCase()))) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Could not find a persist value mode for " + value + ". Use any or all: " + Arrays.toString(values()));
    }

    /**
     * Check if the mode is valid.
     * TODO: Use a new enum PreInstalled instead of PersistSoftware
     * @param value will be checked
     * @return true if its a valid enum
     */
    public static boolean validPersistMode(String value) {
        value = value.replace('.', '_').toUpperCase(Locale.ROOT);
        for (PersistMode mode : values()) {
            if (mode.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PersistenceBuilder apply(PersistenceBuilder builder) {
        return applicator.apply(builder, true);
    }
}
