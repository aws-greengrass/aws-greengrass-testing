package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class CompositeComponentPreparationService implements ComponentPreparationService {
    private final Map<String, ComponentPreparationService> services;

    @Inject
    public CompositeComponentPreparationService(final Map<String, ComponentPreparationService> services) {
        this.services = services;
    }

    @Override
    public Optional<ComponentOverrideNameVersion> prepare(final ComponentOverrideNameVersion name) {
        return Optional.ofNullable(services.get(name.version().type()))
                .map(service -> service.prepare(name))
                .orElseThrow(() -> new IllegalArgumentException("Could not find service for " + name.version().type()));
    }
}
