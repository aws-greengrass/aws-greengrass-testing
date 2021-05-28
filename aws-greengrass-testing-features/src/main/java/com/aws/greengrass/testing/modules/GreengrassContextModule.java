package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.GreengrassContext;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.nio.file.Path;
import java.nio.file.Paths;

@AutoService(Module.class)
public class GreengrassContextModule extends AbstractModule {
    private static final String NUCLEUS_VERSION = "ggc.version";
    private static final String NUCLEUS_ARCHIVE_PATH = "ggc.archive";

    @Provides
    @Singleton
    static GreengrassContext providesNucleusContext() {
        final Path archivePath = Paths.get(System.getProperty(NUCLEUS_ARCHIVE_PATH));
        return GreengrassContext.builder()
                .version(System.getProperty(NUCLEUS_VERSION))
                .archivePath(archivePath)
                .build();
    }
}
