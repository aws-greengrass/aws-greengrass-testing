package com.aws.greengrass.testing.components.streammanager;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = StreamManagerModule.class)
@Singleton
public interface StreamManagerExportComponent {
    S3Export s3();
}
