/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.SubscribeToIoTCoreResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;
import software.amazon.awssdk.aws.greengrass.model.QOS;
import software.amazon.awssdk.aws.greengrass.model.ReportedLifecycleState;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreRequest;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;

public class LocalMqttSubscriber implements Consumer<String[]> {

    private static final int WAIT_TIME_IN_SECONDS = 30;
    private static final String UNSUBSCRIBE_PARAMETER = "unsubscribe";

    private EventStreamRPCConnection eventStreamRPCConnection;
    private GreengrassCoreIPC ipc;
    private IPCUtils ipcUtils;
    private SubscribeToIoTCoreResponseHandler subscribeToIoTCoreResponseHandler;
    private CountDownLatch messageLatch;


    /**
     * Constructor.
     * @param eventStreamRPCConnection ipc connection to nucleus over event stream
     */
    @Inject
    public LocalMqttSubscriber(EventStreamRPCConnection eventStreamRPCConnection) {
        this.eventStreamRPCConnection = eventStreamRPCConnection;
        this.ipc = new GreengrassCoreIPCClient(eventStreamRPCConnection);
        this.ipcUtils = new IPCUtils(ipc);
    }

    @Override
    public void accept(String[] args) {
        EventStreamRPCConnection eventStreamRpcConnection = null;
        try {

            if (args.length < 3) {
                System.err.println("Need more arguments. Arguments: <topic to subscribe> <QOS> <message to expect>");
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
                return;
            }

            String topic = args[0];
            String expectedPayload = args[2];
            QOS qos = ipcUtils.getQOSFromValue(Integer.parseInt(args[1]));

            SubscribeToIoTCoreRequest subscribeToIoTCoreRequest = new SubscribeToIoTCoreRequest();
            subscribeToIoTCoreRequest.setTopicName(topic);
            subscribeToIoTCoreRequest.setQos(qos);

            StreamResponseHandler<IoTCoreMessage> streamResponseHandler = new StreamResponseHandler<IoTCoreMessage>() {
                @Override
                public void onStreamEvent(IoTCoreMessage ioTCoreMessage) {
                    String message = new String(ioTCoreMessage.getMessage().getPayload(), StandardCharsets.UTF_8);
                    System.out.println(String.format("Message received. message=%s, expectedMessage=%s", message,
                            expectedPayload));
                    messageLatch.countDown();
            }

                @Override
                public boolean onStreamError(Throwable throwable) {
                    System.err.println("Subscribe stream errored: " + throwable);
                    return false;
                }

                @Override
                public void onStreamClosed() {

                }
            };

            try {
                subscribeToIoTCoreResponseHandler = ipc.subscribeToIoTCore(
                        subscribeToIoTCoreRequest, Optional.of(streamResponseHandler));
                subscribeToIoTCoreResponseHandler.getResponse().get();
                System.out.println(String.format("Subscribed to IoT topic %s with QOS=%s", topic, qos));
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error occurred while subscribing to IoT topic : " + e);
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
                return;
            }
            ipcUtils.reportState(ReportedLifecycleState.RUNNING);

            waitForMessage();
            waitForMessage();

        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Service errored: " + e);
            if (eventStreamRpcConnection != null) {
                eventStreamRpcConnection.disconnect();
            }
            System.exit(1);
        } finally {
            if (eventStreamRpcConnection != null) {
                eventStreamRpcConnection.disconnect();
            }
        }
    }

    private void waitForMessage() throws ExecutionException, InterruptedException {
        messageLatch = new CountDownLatch(1);
        System.out.println(String.format("Waiting %d seconds for messages", WAIT_TIME_IN_SECONDS));
        try {
            if (!messageLatch.await(WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Timedout waiting for the message");
            }
        } catch (InterruptedException e) {
            System.err.println("Error occurred while waiting for the message: " + e);
            ipcUtils.reportState(ReportedLifecycleState.ERRORED);
        }
    }
}
