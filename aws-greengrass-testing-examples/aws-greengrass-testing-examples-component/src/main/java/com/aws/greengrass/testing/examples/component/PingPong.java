package com.aws.greengrass.testing.examples.component;

import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PingPong implements Runnable {
    PaddleComponent component;

    PingPong(PaddleComponent component) {
        this.component = component;
    }

    @Override
    public void run() {
        try (EventStreamRPCConnection connection = component.connection()) {
            try {
                CompletableFuture<Void> completed = new CompletableFuture<>();
                connection.connect(new EventStreamRPCConnection.LifecycleHandler() {
                    @Override
                    public void onConnect() {
                        System.out.println("Connected to IPC.");
                        completed.complete(null);
                    }

                    @Override
                    public void onDisconnect(int i) {
                    }

                    @Override
                    public boolean onError(Throwable throwable) {
                        completed.completeExceptionally(throwable);
                        return true;
                    }
                });
                completed.get();

                component.ping().watch();
                component.pong().watch();
                component.ping().swing();

                // hit back and forth for a few.
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                // No-op
            } catch (TimeoutException | ExecutionException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public static void main(String[] args) {
        new PingPong(DaggerPaddleComponent.create()).run();
    }
}
