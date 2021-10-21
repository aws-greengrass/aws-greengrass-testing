/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class CompositeComponentPreparationService implements ComponentPreparationService {
    private static final Logger LOGGER = LogManager.getLogger(CompositeComponentPreparationService.class);
    private final Map<String, ComponentPreparationService> services;

    @Inject
    public CompositeComponentPreparationService(final Map<String, ComponentPreparationService> services) {
        this.services = services;
    }

    @Override
    public Optional<ComponentOverrideNameVersion> prepare(final ComponentOverrideNameVersion name) {
        return Optional.ofNullable(services.get(name.version().type()))
                .map(service -> {
                    LOGGER.debug("Selecting the component prep service {} for {} ", service.getClass(), name);
                    return service.prepare(name);
                })
                .orElseThrow(() -> new IllegalArgumentException("Could not find service for " + name.version().type()));
    }
}
