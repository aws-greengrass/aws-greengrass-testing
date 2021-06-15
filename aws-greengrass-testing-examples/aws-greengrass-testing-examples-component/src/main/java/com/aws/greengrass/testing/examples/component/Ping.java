package com.aws.greengrass.testing.examples.component;

import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Ping extends Paddle {
    @Inject
    public Ping(final GreengrassCoreIPC ipc) {
        super(Type.Ping, ipc);
    }
}
