package com.aws.greengrass.testing.components.cloudcomponent;/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.inject.Inject;

public class HelloWorld {

    @Inject
    HelloWorld() {
    }

    public void helloWorld() {
        System.out.println("Hello World!!");
    }

    public void helloWorldUpdated() {
        System.out.println("Hello World Updated!!");
    }
}
