/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.secret;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

@AutoService(Module.class)
public class SecretModule extends AbstractAWSResourceModule<SecretsManagerClient, SecretLifecycle> {
    @Provides
    @Singleton
    @Override
    protected SecretsManagerClient providesClient(
            AwsCredentialsProvider provider,
            AWSResourcesContext context,
            ApacheHttpClient.Builder httpClientBuilder) {
        return SecretsManagerClient.builder()
                .credentialsProvider(provider)
                .region(context.region())
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
