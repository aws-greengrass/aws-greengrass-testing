package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DeleteThingGroupRequest;

@TestingModel
@Value.Immutable
interface IotThingGroupModel extends AWSResource<IotClient> {
    String groupId();
    String groupArn();
    String groupName();

    @Override
    default void remove(IotClient client) {
        client.deleteThingGroup(DeleteThingGroupRequest.builder()
                .thingGroupName(groupName())
                .build());
    }
}
