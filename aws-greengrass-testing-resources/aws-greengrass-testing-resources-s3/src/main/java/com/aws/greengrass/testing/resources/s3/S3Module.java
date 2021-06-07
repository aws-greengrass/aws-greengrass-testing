package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.modules.AbstractAWSResourceModule;
import com.google.auto.service.AutoService;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Singleton;

@AutoService(Module.class)
public class S3Module extends AbstractAWSResourceModule<S3Client, S3Lifecycle> {
    @Provides
    @Singleton
    @Override
    protected S3Client providesClient(
            AwsCredentialsProvider provider,
            Region region,
            ApacheHttpClient.Builder httpClientBuilder) {
        return S3Client.builder()
                .credentialsProvider(provider)
                .region(region)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
