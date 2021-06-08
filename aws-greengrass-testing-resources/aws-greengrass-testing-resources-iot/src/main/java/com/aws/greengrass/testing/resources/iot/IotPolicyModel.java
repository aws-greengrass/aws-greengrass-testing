package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest;

@TestingModel
@Value.Immutable
interface IotPolicyModel extends AWSResource<IotClient> {
    String policyArn();
    String policyName();

    @Override
    default void remove(IotClient client) {
    }
}
