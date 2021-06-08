package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iot.model.CreatePolicyResponse;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotPolicySpecModel extends ResourceSpec<IotClient, IotPolicy> {
    String policyName();
    String policyDocument();

    @Override
    @Nullable
    IotPolicy resource();

    @Override
    default IotPolicySpec create(IotClient client, AWSResources resources) {
        CreatePolicyResponse response = client.createPolicy(CreatePolicyRequest.builder()
                .policyDocument(policyDocument())
                .policyName(policyName())
                .build());
        return IotPolicySpec.builder()
                .from(this)
                .created(true)
                .resource(IotPolicy.builder()
                        .policyArn(response.policyArn())
                        .build())
                .build();
    }
}
