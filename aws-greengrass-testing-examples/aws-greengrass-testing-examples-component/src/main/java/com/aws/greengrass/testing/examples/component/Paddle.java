package com.aws.greengrass.testing.examples.component;

import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.model.BinaryMessage;
import software.amazon.awssdk.aws.greengrass.model.PublishMessage;
import software.amazon.awssdk.aws.greengrass.model.PublishToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

abstract class Paddle {
    private static final int TIMEOUT = 5;
    enum Type {
        Ping, Pong;
    }

    final Type type;
    final GreengrassCoreIPC ipc;

    Paddle(final Type type, final GreengrassCoreIPC ipc) {
        this.ipc = ipc;
        this.type = type;
    }

    public void swing() {
        Type other = Arrays.stream(Type.values()).filter(Predicate.isEqual(type).negate()).findFirst().get();
        PublishMessage publishMessage = new PublishMessage();
        BinaryMessage binaryMessage = new BinaryMessage();
        binaryMessage.setMessage(other.name().getBytes(StandardCharsets.UTF_8));
        publishMessage.setBinaryMessage(binaryMessage);
        PublishToTopicRequest publishToTopicRequest = new PublishToTopicRequest();
        publishToTopicRequest.setTopic("say/" + other.name().toLowerCase());
        publishToTopicRequest.setPublishMessage(publishMessage);
        try {
            ipc.publishToTopic(publishToTopicRequest, Optional.empty()).getResponse().get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // No-op
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void watch() throws InterruptedException, ExecutionException, TimeoutException {
        SubscribeToTopicRequest subscribeToTopicRequest = new SubscribeToTopicRequest();
        subscribeToTopicRequest.setTopic("say/" + type.name().toLowerCase());
        ipc.subscribeToTopic(subscribeToTopicRequest, Optional.of(new StreamResponseHandler<SubscriptionResponseMessage>() {
            @Override
            public void onStreamEvent(SubscriptionResponseMessage subscriptionResponseMessage) {
                String payload = new String(subscriptionResponseMessage.getBinaryMessage().getMessage(),
                        StandardCharsets.UTF_8);
                System.out.println("Received " + payload + " from " + getClass().getSimpleName());
                swing();
            }

            @Override
            public boolean onStreamError(Throwable throwable) {
                System.err.println("Exception thrown in subscription: " + throwable.getMessage());
                return false;
            }

            @Override
            public void onStreamClosed() {

            }
        })).getResponse().get(TIMEOUT, TimeUnit.SECONDS);
    }
}
