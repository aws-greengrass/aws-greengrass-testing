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

    public static CleanupContext fromModes(Collection<PersistMode> modes) {
        return modes.stream()
                .reduce(CleanupContext.builder(),
                        (builder, mode) -> mode.apply(builder),
                        (left, right) -> left).build();
    }
}
