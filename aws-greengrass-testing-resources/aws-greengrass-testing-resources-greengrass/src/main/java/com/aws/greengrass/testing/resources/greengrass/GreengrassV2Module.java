/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2ClientBuilder;

import java.net.URI;

@AutoService(Module.class)
public class GreengrassV2Module extends AbstractAWSResourceModule<GreengrassV2Client, GreengrassV2Lifecycle> {
    @Override
    @Provides
    @Singleton
    protected GreengrassV2Client providesClient(
            AwsCredentialsProvider provider,
            AWSResourcesContext context,
            ApacheHttpClient.Builder httpClientBuilder) {
        GreengrassV2ClientBuilder builder = GreengrassV2Client.builder()
                .credentialsProvider(provider)
                .httpClientBuilder(httpClientBuilder)
                .region(context.region());
        if (!context.isProd()) {
            String endpoint = String.format("https://greengrass-%s.%s.%s", context.envStage(),
                    context.region().metadata().id(), context.region().metadata().domain());
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
