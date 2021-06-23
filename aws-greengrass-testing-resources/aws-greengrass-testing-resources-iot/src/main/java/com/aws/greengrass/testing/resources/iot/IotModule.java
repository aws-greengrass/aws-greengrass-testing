/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.IotClientBuilder;

import java.net.URI;
import javax.inject.Singleton;

@AutoService(Module.class)
public class IotModule extends AbstractAWSResourceModule<IotClient, IotLifecycle> {
    @Singleton
    @Provides
    @Override
    protected IotClient providesClient(
            AwsCredentialsProvider provider,
            AWSResourcesContext context,
            ApacheHttpClient.Builder httpClientBuilder) {
        IotClientBuilder builder = IotClient.builder()
                .credentialsProvider(provider)
                .httpClientBuilder(httpClientBuilder)
                .region(context.region());
        if (!context.isProd()) {
            String endpoint = String.format("https://%s.%s.iot.%s",
                    context.envStage(),
                    context.region().metadata().id(),
                    context.region().metadata().domain());
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
