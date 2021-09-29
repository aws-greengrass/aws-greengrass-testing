package com.aws.greengrass.testing.components.cloudcomponent;/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

public class CloudComponentTest {
    public static final String COMPONENT_NAME_SYS_PROP = "componentName";

    /**
     * Main method will call the required com.aws.greengrass.testing.components.cloudcomponent.HelloWorld artifact.
     *
     * @param args System Arguments
     */
    public static void main(String[] args) {
        String componentName = System.getProperty(COMPONENT_NAME_SYS_PROP);
        if (componentName.equals("HelloWorld")) {
            DaggerCloudComponent.create().getHelloWorld().helloWorld();
        } else if (componentName.equals("HelloWorldUpdated")) {
            DaggerCloudComponent.create().getHelloWorld().helloWorldUpdated();
        }
    }
}
