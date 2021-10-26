/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.secret;


import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class SecretLifecycle extends AbstractAWSResourceLifecycle<SecretsManagerClient> {
    @Inject
    public SecretLifecycle(SecretsManagerClient client) {
        super(client, SecretSpec.class);
    }

    public SecretLifecycle() {
        this(SecretsManagerClient.builder().build());
    }
}
