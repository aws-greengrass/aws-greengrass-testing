/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import io.cucumber.guice.ScenarioScoped;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

public abstract class AbstractAWSResourceModule<C, U extends AWSResourceLifecycle<C>> extends AbstractModule {

    protected abstract C providesClient(
            AwsCredentialsProvider provider,
            AWSResourcesContext context,
            ApacheHttpClient.Builder httpClientBuilder);

    @ProvidesIntoSet
    @ScenarioScoped
    protected AWSResourceLifecycle providesLifeCycle(U lifecycle) {
        return lifecycle;
    }
}
