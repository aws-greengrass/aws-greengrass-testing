package com.aws.greengrass.testing.examples.component;

import dagger.Component;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import javax.inject.Singleton;

@Component(modules = IPCModule.class)
@Singleton
public interface PaddleComponent {
    Pong pong();
    Ping ping();
}
