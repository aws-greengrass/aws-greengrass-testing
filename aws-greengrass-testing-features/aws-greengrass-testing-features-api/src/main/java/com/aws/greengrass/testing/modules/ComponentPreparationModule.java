/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.component.ClasspathComponentPreparationService;
import com.aws.greengrass.testing.component.CloudComponentPreparationService;
import com.aws.greengrass.testing.component.CompositeComponentPreparationService;
import com.aws.greengrass.testing.component.FileComponentPreparationService;
import com.aws.greengrass.testing.component.LocalComponentPreparationService;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import io.cucumber.guice.ScenarioScoped;

@AutoService(Module.class)
public class ComponentPreparationModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ComponentPreparationService.class).to(CompositeComponentPreparationService.class).in(ScenarioScoped.class);
    }

    @ProvidesIntoMap
    @StringMapKey("cloud")
    @ScenarioScoped
    static ComponentPreparationService providesCloudPreparationService(CloudComponentPreparationService service) {
        return service;
    }

    @ProvidesIntoMap
    @StringMapKey("file")
    @ScenarioScoped
    static ComponentPreparationService providesFilePreparationService(FileComponentPreparationService service) {
        return service;
    }

    @ProvidesIntoMap
    @StringMapKey("classpath")
    @ScenarioScoped
    static ComponentPreparationService providesClasspathPreparationService(
            ClasspathComponentPreparationService service) {
        return service;
    }

    @ProvidesIntoMap
    @StringMapKey("local")
    @ScenarioScoped
    static ComponentPreparationService providesClasspathPreparationService(
            LocalComponentPreparationService service) {
        return service;
    }
}
