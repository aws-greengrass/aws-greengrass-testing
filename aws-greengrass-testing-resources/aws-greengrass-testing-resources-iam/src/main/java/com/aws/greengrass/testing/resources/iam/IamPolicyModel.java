package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.EntityType;
import software.amazon.awssdk.services.iam.model.ListEntitiesForPolicyRequest;
import software.amazon.awssdk.services.iam.model.PolicyRole;

@TestingModel
@Value.Immutable
interface IamPolicyModel extends AWSResource<IamClient> {
    String policyArn();

    @Override
    default void remove(IamClient client) {
        for (PolicyRole policy : client.listEntitiesForPolicyPaginator(ListEntitiesForPolicyRequest.builder()
                .policyArn(policyArn())
                .entityFilter(EntityType.ROLE)
                .build()).policyRoles()) {
            client.detachRolePolicy(DetachRolePolicyRequest.builder()
                    .roleName(policy.roleName())
                    .policyArn(policyArn())
                    .build());
        }
        client.deletePolicy(DeletePolicyRequest.builder()
                .policyArn(policyArn())
                .build());
    }
}
