package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;

@TestingModel
@Value.Immutable
interface IamPolicyModel extends AWSResource<IamClient> {
    String policyArn();

    @Override
    default void remove(IamClient client) {
        client.deletePolicy(DeletePolicyRequest.builder()
                .policyArn(policyArn())
                .build());
    }
}
