/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest;
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.CreateCertificateFromCsrRequest;
import software.amazon.awssdk.services.iot.model.CreateCertificateFromCsrResponse;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.utils.StringUtils;

import java.util.Optional;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotCertificateSpecModel extends ResourceSpec<IotClient, IotCertificate>, IotTaggingMixin {
    @Value.Default
    default boolean active() {
        return true;
    }

    String thingName();

    @Nullable
    IotPolicySpec policy();

    @Nullable
    String csr();

    @Nullable
    String existingArn();

    @Override
    default IotCertificateSpec create(IotClient client, AWSResources resources) {
        if (!StringUtils.isEmpty(csr())) {
            return createCertificateFromCsr(client, resources);
        } else {
            return createCertificate(client, resources);
        }
    }

    default IotCertificateSpec createCertificateFromCsr(IotClient client, AWSResources resources) {
        CreateCertificateFromCsrResponse created = client
                .createCertificateFromCsr(CreateCertificateFromCsrRequest.builder()
                        .setAsActive(active())
                        .certificateSigningRequest(csr()).build());

        client.attachThingPrincipal(AttachThingPrincipalRequest.builder()
                .thingName(thingName())
                .principal(created.certificateArn())
                .build());

        Optional.ofNullable(policy()).ifPresent(spec -> {
            client.attachPolicy(AttachPolicyRequest.builder()
                    .policyName(resources.create(spec).policyName())
                    .target(created.certificateArn())
                    .build());
        });

        return IotCertificateSpec.builder()
                .from(this)
                .created(true)
                .resource(IotCertificate.builder()
                        .certificateArn(created.certificateArn())
                        .certificateId(created.certificateId())
                        .certificatePem(created.certificatePem())
                        .build())
                .build();
    }

    default IotCertificateSpec createCertificate(IotClient client, AWSResources resources) {
        CreateKeysAndCertificateResponse created = client.createKeysAndCertificate(
                CreateKeysAndCertificateRequest.builder()
                        .setAsActive(active())
                        .build());
        client.attachThingPrincipal(AttachThingPrincipalRequest.builder()
                .thingName(thingName())
                .principal(created.certificateArn())
                .build());

        Optional.ofNullable(policy()).ifPresent(spec -> {
            client.attachPolicy(AttachPolicyRequest.builder()
                    .policyName(resources.create(spec).policyName())
                    .target(created.certificateArn())
                    .build());
        });

        return IotCertificateSpec.builder()
                .from(this)
                .created(true)
                .resource(IotCertificate.builder()
                        .keyPair(created.keyPair())
                        .certificateArn(created.certificateArn())
                        .certificateId(created.certificateId())
                        .certificatePem(created.certificatePem())
                        .build())
                .build();
    }

    @Nullable
    IotCertificate resource();
}
