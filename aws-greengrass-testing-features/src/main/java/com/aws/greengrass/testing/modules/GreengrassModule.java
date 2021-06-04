package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.DefaultGreengrass;
import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

@AutoService(Module.class)
public class GreengrassModule extends AbstractModule {
    @Provides
    @ScenarioScoped
    static Greengrass providesGreengrass(
            final Platform platform,
            final TestContext testContext,
            final AWSResourcesContext resourcesContext) {
        return new DefaultGreengrass(platform,
                resourcesContext.envStage(),
                resourcesContext.region().id(),
                testContext.testDirectory(),
                testContext);
    }
}
