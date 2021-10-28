/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPC;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.GetSecretValueRequest;
import software.amazon.awssdk.aws.greengrass.model.GetSecretValueResponse;
import software.amazon.awssdk.aws.greengrass.model.ReportedLifecycleState;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.inject.Inject;

public class SecretManagerComponents implements Consumer<String[]> {

    private static final Logger logger = LogManager.getLogger(SecretManagerComponents.class);

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
        this.ipcUtils = new IPCUtils(ipc);
    }

    @Override
    public void accept(String[] strings) {
        try {
            if (strings.length < 1) {
                System.err.println("Need more arguments.");
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
                return;
            }
            GetSecretValueRequest request = new GetSecretValueRequest();

            String secretId = strings[0];
            logger.info("Configured secret id: " + secretId);
            request.setSecretId(secretId);

            String versionStage = null;
            try {
                versionStage = strings[1];
                if (versionStage != null && !versionStage.equals("null")) {
                    request.setVersionStage(versionStage);
                }
                logger.info("Configured version stage: " + versionStage);
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.info("No configured version stage");
                // ignore
            }
            String versionId = null;
            try {
                versionId = strings[2];
                if (versionId != null && !versionId.equals("null")) {
                    request.setVersionId(versionId);
                }
                logger.info("Configured version id: " + versionId);
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.info("No configured version id");
                // ignore
            }

            Gson gson = new Gson();
            GetSecretValueResponse result =
                    ipc.getSecretValue(request, Optional.empty()).getResponse().get();
            result.getSecretValue().postFromJson();
            System.out.println("Got secret response " + new String(result.toPayload(gson)));

        } catch (ExecutionException | InterruptedException e) {
            if (eventStreamRPCConnection != null) {
                eventStreamRPCConnection.disconnect();
            }
            try {
                ipcUtils.reportState(ReportedLifecycleState.ERRORED);
            } catch (ExecutionException executionException) {
                executionException.printStackTrace();
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
            logger.error("Error occurred while get secret : " + e);

            System.exit(1);

        } finally {
            if (this.eventStreamRPCConnection != null) {

                this.eventStreamRPCConnection.close();

            }
        }
    }
}
