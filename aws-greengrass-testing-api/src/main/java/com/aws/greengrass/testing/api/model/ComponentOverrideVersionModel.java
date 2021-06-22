package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface ComponentOverrideVersionModel {
    String type();

    String value();
}
