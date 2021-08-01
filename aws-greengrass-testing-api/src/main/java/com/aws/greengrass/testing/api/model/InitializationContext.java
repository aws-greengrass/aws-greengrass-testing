/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Collection;
import java.util.Set;

@Value.Immutable
@Value.Style(jdkOnly = true, visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class InitializationContext {

    public abstract Set<PersistMode> persistModes();

    @Value.Default
    public boolean persistAWSResources() {
        return false;
    }

    @Value.Default
    public boolean persistInstalledSoftware() {
        return false;
    }

    @Value.Default
    public boolean persistGeneratedFiles() {
        return false;
    }

    public static Builder builder() {
        return ImmutableInitializationContext.builder();
    }

    public interface Builder extends PersistenceBuilder<Builder> {
        public Builder addPersistModes(PersistMode mode);

        public Builder addPersistModes(PersistMode...modes);

        InitializationContext build();
    }

    /**
     * Creates a test run initialization context that mirrors the {@link CleanupContext}.
     *
     * @param modes a {@link Collection} of {@link PersistMode}s
     * @return
     */
    public static InitializationContext fromModes(Collection<PersistMode> modes) {
        final Builder builder = builder();
        modes.forEach(mode -> mode.apply(builder.addPersistModes(mode)));
        return builder.build();
    }
}
