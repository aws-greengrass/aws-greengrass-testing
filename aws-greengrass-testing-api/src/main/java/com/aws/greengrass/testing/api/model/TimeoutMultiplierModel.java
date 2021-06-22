package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.Objects;
import javax.annotation.Nonnull;

@TestingModel
@Value.Immutable
interface TimeoutMultiplierModel {
    @Value.Default
    default double multiplier() {
        String value = System.getenv("TIMEOUT_MULTIPLIER");
        if (Objects.nonNull(value)) {
            return Double.parseDouble(value);
        } else {
            return 1.0;
        }
    }

    default long multiply(@Nonnull Number value) {
        return Math.round(multiplier() * value.doubleValue());
    }
}
