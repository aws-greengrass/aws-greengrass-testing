/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

public class TestRouter {

    public static final String MQTT_PUBSUB_OPERATION = "mqttPubsub";
    public static final String IPC_OPERATION_SYS_PROP = "ipc.operation";

    /**
     * Main entry method.
     * @param args arguments to main
     */
    public static void main(String[] args) {
        String operationName = System.getProperty(IPC_OPERATION_SYS_PROP);

        switch (operationName) {
            case MQTT_PUBSUB_OPERATION: {
                DaggerMqttComponents.create().getMqttPubsubComponents().accept(args);
                break;
            }
            default: {
                System.err.println("Unsupported ipc operation");
                break;
            }
        }
    }
}
