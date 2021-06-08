package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@TestingModel
@Value.Immutable
interface ComponentOverridesModel {
    @Nullable
    String bucketName();

    Map<String, ComponentOverrideVersion> overrides();

    default ComponentOverrideNameVersion component(final String name) {
        return ComponentOverrideNameVersion.builder()
                .name(name)
                .version(overrides().getOrDefault(name, ComponentOverrideVersion.of("cloud", "LATEST")))
                .build();
    }
}
