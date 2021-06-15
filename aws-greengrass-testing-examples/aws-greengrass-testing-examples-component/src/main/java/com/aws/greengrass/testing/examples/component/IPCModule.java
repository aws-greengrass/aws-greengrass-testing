package com.aws.greengrass.testing.examples.component;

import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnectionConfig;
import software.amazon.awssdk.eventstreamrpc.GreengrassConnectMessageSupplier;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class IPCModule {
    private static final String IPC_SERVER_PATH = "AWS_GG_NUCLEUS_DOMAIN_SOCKET_FILEPATH_FOR_COMPONENT";
    private static final String AUTH_TOKEN = "SVCUID";
    private static final int DEFAULT_PORT = 8033;

    @Provides
    @Singleton
    @Named(IPC_SERVER_PATH)
    static String providesIpcServerPath() {
        return System.getenv(IPC_SERVER_PATH);
    }

    @Provides
    @Singleton
    @Named(AUTH_TOKEN)
    static String providesAuthToken() {
        return System.getenv(AUTH_TOKEN);
    }

    @Provides
    @Singleton
    static SocketOptions providesSocketOptions() {
        SocketOptions socketOptions = new SocketOptions();
        socketOptions.connectTimeoutMs = 3000;
        socketOptions.domain = SocketOptions.SocketDomain.LOCAL;
        socketOptions.type = SocketOptions.SocketType.STREAM;
        return socketOptions;
    }

    @Provides
    @Singleton
    static EventStreamRPCConnection providesEventConnection(
            SocketOptions socketOptions,
            @Named(IPC_SERVER_PATH) final String ipcServerPath,
            @Named(AUTH_TOKEN) final String authToken) {
        try (EventLoopGroup loopGroup = new EventLoopGroup(2);
             ClientBootstrap clientBootstrap = new ClientBootstrap(loopGroup, null)) {
            EventStreamRPCConnectionConfig connectionConfig = new EventStreamRPCConnectionConfig(
                    clientBootstrap, loopGroup, socketOptions, null, ipcServerPath, DEFAULT_PORT,
                    GreengrassConnectMessageSupplier.connectMessageSupplier(authToken));
            return new EventStreamRPCConnection(connectionConfig);
        }
    }

    @Provides
    @Singleton
    static GreengrassCoreIPC providesCoreIPC(EventStreamRPCConnection connection) {
        return new GreengrassCoreIPCClient(connection);
    }
}
