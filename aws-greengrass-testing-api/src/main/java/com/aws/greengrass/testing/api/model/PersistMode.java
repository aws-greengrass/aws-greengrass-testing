package com.aws.greengrass.testing.api.model;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

public enum PersistMode implements Function<ImmutableCleanupContext.Builder, ImmutableCleanupContext.Builder> {
    AWS_RESOURCES(ImmutableCleanupContext.Builder::persistAWSResources),
    INSTALLED_SOFTWARE(ImmutableCleanupContext.Builder::persistInstalledSofware),
    GENERATED_FILES(ImmutableCleanupContext.Builder::persistGeneratedFiles);

    BiFunction<ImmutableCleanupContext.Builder, Boolean, ImmutableCleanupContext.Builder> applicator;

    PersistMode(BiFunction<ImmutableCleanupContext.Builder, Boolean, ImmutableCleanupContext.Builder> applicator) {
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

    @Override
    public ImmutableCleanupContext.Builder apply(ImmutableCleanupContext.Builder builder) {
        return applicator.apply(builder, true);
    }
}
