package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

@Value.Style(
        jdkOnly = true,
        typeImmutable = "*",
        typeModifiable = "*",
        typeAbstract = "*Model",
        allMandatoryParameters = true,
        visibility = Value.Style.ImplementationVisibility.PUBLIC
)
public @interface TestingModel {
}
