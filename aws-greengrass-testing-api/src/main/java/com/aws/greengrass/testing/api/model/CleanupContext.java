/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Collection;


@Value.Immutable
@Value.Style(jdkOnly = true, visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class CleanupContext {
    @Value.Default
    public boolean persistAWSResources() {
        return false;
    }

    @Value.Default
    public boolean persistInstalledSofware() {
        return false;
    }

    @Value.Default
    public boolean persistGeneratedFiles() {
        return false;
    }

    public static ImmutableCleanupContext.Builder builder() {
        return ImmutableCleanupContext.builder();
    }

    /**
     * Create a concrete {@link CleanupContext} from a collection of {@link PersistMode}.
     *
     * @param modes User provided {@link PersistMode} to be used to bypass cleanup.
     * @return
     */
    public static CleanupContext fromModes(Collection<PersistMode> modes) {
        return modes.stream()
                .reduce(CleanupContext.builder(), (builder, mode) -> mode.apply(builder), (left, right) -> left)
                .build();
    }
}
