package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.iam.IamClient;

import javax.inject.Singleton;

@AutoService(Module.class)
public class IamModule extends AbstractAWSResourceModule<IamClient, IamLifecycle> {
    @Provides
    @Singleton
    @Override
    protected IamClient providesClient(AwsCredentialsProvider provider) {
        return IamClient.builder()
                .credentialsProvider(provider)
                .build();
    }
}
