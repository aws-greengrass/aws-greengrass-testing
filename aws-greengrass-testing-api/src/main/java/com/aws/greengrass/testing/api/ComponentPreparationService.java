package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;

import java.util.Optional;

public interface ComponentPreparationService {
    Optional<ComponentOverrideNameVersion> prepare(ComponentOverrideNameVersion version);
}
