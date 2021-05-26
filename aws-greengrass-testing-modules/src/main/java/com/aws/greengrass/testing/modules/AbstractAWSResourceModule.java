package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.cucumber.guice.ScenarioScoped;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public abstract class AbstractAWSResourceModule<C, U extends AWSResourceLifecycle<C>> extends AbstractModule {

    protected abstract C providesClient(AwsCredentialsProvider provider);

    @ProvidesIntoSet
    @ScenarioScoped
    protected AWSResourceLifecycle providesLifeCycle(U lifecycle) {
        return lifecycle;
    }
}
