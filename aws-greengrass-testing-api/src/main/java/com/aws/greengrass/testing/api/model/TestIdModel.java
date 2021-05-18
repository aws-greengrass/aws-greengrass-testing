package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface TestIdModel {
    String id();

    default String idFor(String type) {
        return String.format("%s-%s", id(), type);
    }
}
