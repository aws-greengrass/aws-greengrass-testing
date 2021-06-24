/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.examples.component;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = IPCModule.class)
@Singleton
public interface PaddleComponent {
    Pong pong();

    Ping ping();
}
