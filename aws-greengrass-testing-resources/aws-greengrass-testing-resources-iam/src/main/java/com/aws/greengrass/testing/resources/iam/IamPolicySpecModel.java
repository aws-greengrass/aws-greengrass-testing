/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IamPolicySpecModel extends ResourceSpec<IamClient, IamPolicy>, IamTaggingMixin {
    String policyName();

    String policyDocument();

    @Override
    default IamPolicySpec create(IamClient client, AWSResources resources) {
        CreatePolicyResponse createdPolicy = client.createPolicy(CreatePolicyRequest.builder()
                .policyDocument(policyDocument())
                .policyName(policyName())
                .tags(convertTags(resources.generateResourceTags()))
                .build());
        return IamPolicySpec.builder()
                .from(this)
                .created(true)
                .resource(IamPolicy.builder()
                        .policyArn(createdPolicy.policy().arn())
                        .build())
                .build();
    }

    @Nullable
    IamPolicy resource();
}
