package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.component.CloudComponentPreparationService;
import com.aws.greengrass.testing.component.CompositeComponentPreparationService;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
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
}
