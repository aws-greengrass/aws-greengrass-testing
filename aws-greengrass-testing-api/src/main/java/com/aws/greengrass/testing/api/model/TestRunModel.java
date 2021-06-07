package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.Duration;

@TestingModel
@Value.Immutable
interface TestRunModel {
    String name();

    @Nullable
    Duration duration();

    @Nullable
    String message();

    @Value.Default
    default boolean skipped() {
        return false;
    }

    @Value.Default
    default boolean failed() {
        return false;
    }

    @Value.Default
    default boolean passed() {
        return false;
    }
}