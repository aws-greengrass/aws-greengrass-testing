/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateStatus;
import software.amazon.awssdk.services.iot.model.DeleteCertificateRequest;
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest;
import software.amazon.awssdk.services.iot.model.KeyPair;
import software.amazon.awssdk.services.iot.model.ListAttachedPoliciesRequest;
import software.amazon.awssdk.services.iot.model.Policy;
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest;

@TestingModel
@Value.Immutable
interface IotCertificateModel extends AWSResource<IotClient> {
    String certificateArn();

    String certificateId();

    String certificatePem();

    KeyPair keyPair();

    @Override
    default void remove(IotClient client) {
        for (Policy policy : client.listAttachedPoliciesPaginator(ListAttachedPoliciesRequest.builder()
                .target(certificateArn())
                .build())
                .policies()) {
            client.detachPolicy(DetachPolicyRequest.builder()
                    .policyName(policy.policyName())
                    .target(certificateArn())
                    .build());
        }
        client.updateCertificate(UpdateCertificateRequest.builder()
                .newStatus(CertificateStatus.INACTIVE)
                .certificateId(certificateId())
                .build());
        client.deleteCertificate(DeleteCertificateRequest.builder()
                .certificateId(certificateId())
                .forceDelete(true)
                .build());
    }
}
