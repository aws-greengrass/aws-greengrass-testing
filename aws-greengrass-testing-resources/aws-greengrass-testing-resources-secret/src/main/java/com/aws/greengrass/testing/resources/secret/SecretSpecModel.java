/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.secret;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerResponse;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface SecretSpecModel extends ResourceSpec<SecretsManagerClient, Secret> {
    String secretId();

    String secretValue();

    @Nullable
    @Override
    Secret resource();

    @Override
    default SecretSpec create(SecretsManagerClient client, AWSResources resources) {

        SecretsManagerResponse response = client.createSecret(
                CreateSecretRequest.builder()
                        .name(secretId())
                        .secretString(secretValue())
                        .build());

        return SecretSpec.builder()
                .from(this)
                .created(true)
                .resource(Secret.builder()
                        .secretId(secretId())
                        .secretValue(secretValue())
                        .build())
                .build();
    }
}
