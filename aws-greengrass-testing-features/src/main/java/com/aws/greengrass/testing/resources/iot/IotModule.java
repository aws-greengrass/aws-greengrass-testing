package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iot.IotClient;

import javax.inject.Singleton;

@AutoService(Module.class)
public class IotModule extends AbstractAWSResourceModule<IotClient, IotLifecycle> {
    @Singleton
    @Provides
    @Override
    protected IotClient providesClient(AwsCredentialsProvider provider, Region region) {
        return IotClient.builder()
                .credentialsProvider(provider)
                .region(region)
                .build();
    }
}
