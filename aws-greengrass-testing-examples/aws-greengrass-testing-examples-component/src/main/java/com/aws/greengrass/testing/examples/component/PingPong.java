/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.examples.component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PingPong implements Runnable {
    PaddleComponent component;

    PingPong(PaddleComponent component) {
        this.component = component;
    }

    @Override
    public void run() {
        try {
            component.ping().watch();
            component.pong().watch();
            component.ping().swing();
            Thread.sleep(TimeUnit.SECONDS.toMillis(30));
        } catch (InterruptedException e) {
            // No-op
        } catch (TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new PingPong(DaggerPaddleComponent.create()).run();
    }
}
