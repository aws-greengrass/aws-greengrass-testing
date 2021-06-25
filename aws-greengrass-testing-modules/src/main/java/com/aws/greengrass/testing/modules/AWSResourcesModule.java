/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AWSResources;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Singleton;

@AutoService(Module.class)
public class AWSResourcesModule extends AbstractModule {
    private static final String PROXY_URL = "proxy.url";
    private static final String ENV_STAGE = "env.stage";

    @Provides
    @ScenarioScoped
    static AWSResources providesAWSResources(
            Set<AWSResourceLifecycle> lifecycles,
            CleanupContext cleanupContext,
            TestId testId) {
        return new AWSResources(lifecycles, cleanupContext, testId);
    }

    @Provides
    @Singleton
    static Region providesRegion() {
        return DefaultAwsRegionProviderChain.builder().build()
                .getRegion();
    }

    @Provides
    @Singleton
    static Optional<ProxyConfig> providesProxyConfiguration() {
        String proxyUrl = System.getProperty(PROXY_URL);
        return Optional.ofNullable(proxyUrl).map(ProxyConfig::fromURL);
    }

    @Provides
    @Singleton
    static AWSResourcesContext providesAWSResourcesContext(
            final Optional<ProxyConfig> proxyConfig,
            final Region region) {
        // -Denv.stage or ENV_STAGE environment or prod
        final String stage = Optional.ofNullable(System.getProperty(ENV_STAGE, System.getenv("ENV_STAGE")))
                .orElse("prod");
        return AWSResourcesContext.builder()
                .envStage(stage)
                .proxyConfig(proxyConfig)
                .region(region)
                .build();
    }

    @Provides
    @Singleton
    static ApacheHttpClient.Builder provideApacheHttpClientBuilder(final Optional<ProxyConfig> proxyConfig) {
        final ApacheHttpClient.Builder builder = ApacheHttpClient.builder();
        proxyConfig.ifPresent(config -> {
            final ProxyConfiguration.Builder proxy = ProxyConfiguration.builder();
            if (Objects.nonNull(config.username())) {
                proxy.username(config.username()).password(config.password());
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(config.username(), config.password().toCharArray());
                    }
                });
            }
            proxy.endpoint(config.toURI());
            builder.proxyConfiguration(proxy.build());
        });
        return builder;
    }

    @Provides
    @Singleton
    static AwsCredentialsProvider providesAwsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }
}
