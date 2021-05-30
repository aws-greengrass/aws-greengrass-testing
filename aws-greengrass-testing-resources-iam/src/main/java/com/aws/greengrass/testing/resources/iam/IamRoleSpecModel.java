package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
@JsonDeserialize(builder = IamRoleSpec.Builder.class)
interface IamRoleSpecModel extends ResourceSpec<IamClient, IamRole> {
    @Nullable
    String policyArn();
    String roleName();
    String policyDocument();
    String trustDocument();

    @Override
    default IamRoleSpec create(IamClient client, AWSResources resources) {
        CreateRoleResponse createdRole = client.createRole(CreateRoleRequest.builder()
                .roleName(roleName())
                .assumeRolePolicyDocument(trustDocument())
                .build());

        IamPolicySpec policySpec = resources.create(IamPolicySpec.builder()
                .policyName(roleName() + "-policy")
                .policyDocument(policyDocument())
                .build());

        client.attachRolePolicy(AttachRolePolicyRequest.builder()
                .roleName(roleName())
                .policyArn(policySpec.resource().policyArn())
                .build());

        return IamRoleSpec.builder()
                .from(this)
                .created(true)
                .resource(IamRole.builder()
                        .roleName(createdRole.role().roleName())
                        .roleArn(createdRole.role().arn())
                        .build())
                .build();
    }

    @Nullable
    IamRole resource();
}