package com.aws.greengrass.testing.features.modules;

import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;

import javax.inject.Singleton;

@AutoService(Module.class)
public class MQTTModule extends AbstractModule {
    private static final int THREADS = 1;

    @Provides
    @Singleton
    static EventLoopGroup providesEventLoopGroup() {
        return new EventLoopGroup(THREADS);
    }

    @Provides
    @Singleton
    static ClientBootstrap providesClientBootstrap(final EventLoopGroup loopGroup, final HostResolver resolver) {
        return new ClientBootstrap(loopGroup, resolver);
    }

    @Provides
    @Singleton
    static HostResolver providesHostResolver(final EventLoopGroup loopGroup) {
        return new HostResolver(loopGroup);
    }
}
