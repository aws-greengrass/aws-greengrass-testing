package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.TestId;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

import java.util.UUID;

@AutoService(Module.class)
public class TestIdModule extends AbstractModule {
    @Provides
    @ScenarioScoped
    static TestId providesTestId() {
        return TestId.builder()
                .id(UUID.randomUUID().toString())
                .build();
    }
}
