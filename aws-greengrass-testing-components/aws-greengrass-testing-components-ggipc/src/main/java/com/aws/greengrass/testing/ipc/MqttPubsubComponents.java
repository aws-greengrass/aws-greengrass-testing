/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import java.util.function.Consumer;
import javax.inject.Inject;

public class MqttPubsubComponents implements Consumer<String[]> {
    public static final String IOT_MQTT_PUBLISHER = "IotMqttPublisher";
    public static final String IOT_MQTT_SUBSCRIBER = "IotMqttSubscriber";
    public static final String COMPONENT_NAME_SYS_PROP = "componentName";

    @Inject
    public MqttPubsubComponents() {
        // For dagger injection
    }

    /**
     * Runs a component with the specified name.
     * @param args command line args
     */
    @Override
    public void accept(String[] args) {
        String componentName = System.getProperty(COMPONENT_NAME_SYS_PROP);
        switch (componentName) {
            case IOT_MQTT_PUBLISHER: {
                DaggerMqttComponents.create().getPublisher().accept(args);
                break;
            } case IOT_MQTT_SUBSCRIBER: {
                DaggerMqttComponents.create().getSubscriber().accept(args);
                break;
            }
            default: {
                System.err.println("Incorrect mqtt component");
                break;
            }
        }
    }
}
