package com.aws.greengrass.testing.examples.standalone;

import com.aws.greengrass.testing.api.model.TestId;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

import java.nio.file.Path;
import java.nio.file.Paths;

@AutoService(Module.class)
public class ExampleStepsModule extends AbstractModule {
    @Provides
    @ScenarioScoped
    static Path providesTestIdPath(TestId testId) {
        return Paths.get(testId.idFor("examples"));
    }
}
