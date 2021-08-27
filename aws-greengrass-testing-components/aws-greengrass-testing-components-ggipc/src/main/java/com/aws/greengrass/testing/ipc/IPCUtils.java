/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import org.checkerframework.checker.nullness.qual.NonNull;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.model.QOS;
import software.amazon.awssdk.aws.greengrass.model.ReportedLifecycleState;
import software.amazon.awssdk.aws.greengrass.model.UpdateStateRequest;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

public class IPCUtils {
    private GreengrassCoreIPC ipc;

    @Inject
    public IPCUtils(GreengrassCoreIPC ipc) {
        this.ipc = ipc;
    }

    /**
     * Returns enum representation of the QOS.
     * @param qos qos as integer
     * @return {@link QOS}
     */
    public QOS getQOSFromValue(int qos) {
        if (qos == 1) {
            return QOS.AT_LEAST_ONCE;
        } else if (qos == 0) {
            return QOS.AT_MOST_ONCE;
        }
        return QOS.AT_LEAST_ONCE; //default value
    }

    /**
     * Reports state of a component to nucleus.
     * @param state state to be reported {@link ReportedLifecycleState}
     * @throws ExecutionException Runtime error in sending the state to nucleus
     * @throws InterruptedException Task interrupted
     */
    public void reportState(@NonNull ReportedLifecycleState state)
            throws ExecutionException, InterruptedException {
        UpdateStateRequest updateStateRequest = new UpdateStateRequest();
        updateStateRequest.setState(state);
        this.ipc.updateState(updateStateRequest, Optional.empty()).getResponse().get();
    }
}
