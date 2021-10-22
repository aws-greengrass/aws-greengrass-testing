/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import com.google.gson.Gson;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.GetSecretValueRequest;
import software.amazon.awssdk.aws.greengrass.model.GetSecretValueResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.inject.Inject;

public class SecretManagerComponents implements Consumer<String[]> {

    private EventStreamRPCConnection eventStreamRPCConnection;
    private GreengrassCoreIPC ipc;
    private IPCUtils ipcUtils;

    /**
     * Constructor.
     * @param eventStreamRPCConnection ipc connection to nucleus over event stream
     */
    @Inject
    public SecretManagerComponents(EventStreamRPCConnection eventStreamRPCConnection) {
        this.eventStreamRPCConnection = eventStreamRPCConnection;
        this.ipc = new GreengrassCoreIPCClient(eventStreamRPCConnection);
    }

    @Override
    public void accept(String[] strings) {
        EventStreamRPCConnection eventStreamRpcConnection = null;
        try {

            GetSecretValueRequest request = new GetSecretValueRequest();

            String secretId = strings[0];
            System.out.println("Configured secret id: " + secretId);
            request.setSecretId(secretId);

            String versionStage = null;
            try {
                versionStage = strings[1];
                if (versionStage != null && !versionStage.equals("null")) {
                    request.setVersionStage(versionStage);
                }
                System.out.println("Configured version stage: " + versionStage);
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("No configured version stage");
                // ignore
            }
            String versionId = null;
            try {
                versionId = strings[2];
                if (versionId != null && !versionId.equals("null")) {
                    request.setVersionId(versionId);
                }
                System.out.println("Configured version id: " + versionId);
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("No configured version id");
                // ignore
            }

            Gson gson = new Gson();
            GetSecretValueResponse result =
                    ipc.getSecretValue(request, Optional.empty()).getResponse().get();
            result.getSecretValue().postFromJson();
            System.out.println("Got secret response " + new String(result.toPayload(gson)));


        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error occurred while get secret : " + e);
        } finally {
            if (eventStreamRpcConnection != null) {

                eventStreamRpcConnection.close();

            }
        }
    }
}
