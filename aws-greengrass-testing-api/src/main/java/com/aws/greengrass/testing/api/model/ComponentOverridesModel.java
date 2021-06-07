package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Map;

@TestingModel
@Value.Immutable
interface ComponentOverridesModel {
    Map<String, ComponentOverrideVersion> overrides();

    default ComponentOverrideNameVersion component(final String name) {
        return ComponentOverrideNameVersion.builder()
                .name(name)
                .version(overrides().getOrDefault(name, ComponentOverrideVersion.of("cloud", "LATEST")))
                .build();
    }
}
