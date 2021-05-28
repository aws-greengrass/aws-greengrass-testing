package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.nio.file.Path;

@TestingModel
@Value.Immutable
interface GreengrassContextModel {
    String version();

    Path archivePath();
}
