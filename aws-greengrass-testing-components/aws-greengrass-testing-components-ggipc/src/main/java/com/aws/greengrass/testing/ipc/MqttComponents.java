/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import dagger.Component;

import javax.inject.Singleton;

@Component (modules = IPCModule.class)
@Singleton
public interface MqttComponents {
    LocalMqttPublisher getPublisher();

    LocalMqttSubscriber getSubscriber();

    MqttPubsubComponents getMqttPubsubComponents();
}
