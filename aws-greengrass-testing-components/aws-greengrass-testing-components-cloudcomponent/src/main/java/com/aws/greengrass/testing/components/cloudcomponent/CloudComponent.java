package com.aws.greengrass.testing.components.cloudcomponent;/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import dagger.Component;

import javax.inject.Singleton;

@Component
@Singleton
public interface CloudComponent {
    HelloWorld getHelloWorld();
}
