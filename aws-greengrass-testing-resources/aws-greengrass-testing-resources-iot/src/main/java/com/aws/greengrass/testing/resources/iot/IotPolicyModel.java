/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest;
import software.amazon.awssdk.services.iot.model.ListTargetsForPolicyRequest;

import java.util.List;

@TestingModel
@Value.Immutable
interface IotPolicyModel extends AWSResource<IotClient> {
    String policyArn();

    String policyName();

    @Override
    default void remove(IotClient client) {
        List<String> targets =
                client.listTargetsForPolicy(ListTargetsForPolicyRequest.builder()
                        .policyName(policyName())
                        .build())
                        .targets();
        if (targets != null) {
            for (String target : targets) {
                client.detachPolicy(DetachPolicyRequest.builder()
                        .target(target)
                        .policyName(policyName())
                        .build());
            }
        }
        client.deletePolicy(DeletePolicyRequest.builder()
                .policyName(policyName())
                .build());
    }
}
