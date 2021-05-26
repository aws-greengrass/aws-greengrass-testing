package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AWSResources;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import io.cucumber.guice.ScenarioScoped;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import javax.inject.Singleton;
import java.util.Set;

@AutoService(Module.class)
public class AWSResourcesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<AWSResourceLifecycle> lifecycles = Multibinder.newSetBinder(binder(), AWSResourceLifecycle.class);
        lifecycles.addBinding().in(ScenarioScoped.class);
    }

    @Provides
    @ScenarioScoped
    static AWSResources providesAWSResources(Set<AWSResourceLifecycle> lifecycles) {
        return new AWSResources(lifecycles);
    }

    @Provides
    @Singleton
    static Region providesRegion() {
        return DefaultAwsRegionProviderChain.builder().build()
                .getRegion();
    }

    @Provides
    @Singleton
    static AwsCredentialsProvider providesAwsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }
}
