/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.PublishToIoTCoreRequest;
import software.amazon.awssdk.aws.greengrass.model.QOS;
import software.amazon.awssdk.aws.greengrass.model.ReportedLifecycleState;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.inject.Inject;

public class LocalMqttPublisher implements Consumer<String[]> {
    private EventStreamRPCConnection eventStreamRPCConnection;
    private GreengrassCoreIPC ipc;
    private IPCUtils ipcUtils;


    /**
     * Constructor.
     * @param eventStreamRPCConnection ipc connection to nucleus over event stream
     */
    @Inject
    public LocalMqttPublisher(EventStreamRPCConnection eventStreamRPCConnection) {
        this.eventStreamRPCConnection = eventStreamRPCConnection;
        this.ipc = new GreengrassCoreIPCClient(eventStreamRPCConnection);
        this.ipcUtils = new IPCUtils(ipc);
    }

    @Override
    public void accept(String[] args) {
        try {

            if (args.length < 3) {
                System.err.println("Need more arguments. Arguments: <topic to publish> <QOS> <message to publish>");
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
                return;
            } else {
                ipcUtils.reportState(ReportedLifecycleState.RUNNING);
            }

            String topic = args[0];
            String payload = args[2];
            QOS qos = ipcUtils.getQOSFromValue(Integer.parseInt(args[1]));


            PublishToIoTCoreRequest publishToIoTCoreRequest = new PublishToIoTCoreRequest();
            publishToIoTCoreRequest.setTopicName(topic);
            publishToIoTCoreRequest.setPayload(payload.getBytes(StandardCharsets.UTF_8));
            publishToIoTCoreRequest.setQos(qos);

            try {
                ipc.publishToIoTCore(publishToIoTCoreRequest, Optional.empty()).getResponse().get();
                System.out.println(String.format("Published to IoT topic %s with payload %s and qos %s",
                        topic, payload, qos));
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error occurred while publishing to IoT topic : " + e);
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
            }

        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Service errored : " + e);
            if (eventStreamRPCConnection != null) {
                eventStreamRPCConnection.disconnect();
            }
            System.exit(1);
        } finally {
            if (eventStreamRPCConnection != null) {
                eventStreamRPCConnection.disconnect();
            }
        }
    }
}
