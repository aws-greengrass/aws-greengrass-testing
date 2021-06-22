package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface ComponentOverridesModel {
    @Nullable
    String bucketName();

    Map<String, ComponentOverrideVersion> overrides();

    default Optional<ComponentOverrideNameVersion> component(final String name) {
        return Optional.ofNullable(overrides().get(name))
                .map(version -> ComponentOverrideNameVersion.builder().name(name).version(version).build());
    }
}
