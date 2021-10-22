/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.secret;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;

@TestingModel
@Value.Immutable
interface SecretModel extends AWSResource<SecretsManagerClient> {
    String secretId();

    String secretValue();

    @Override
    default void remove(SecretsManagerClient client) {
        client.deleteSecret(DeleteSecretRequest.builder()
                .secretId(secretId()).forceDeleteWithoutRecovery(true).build());
    }
}
