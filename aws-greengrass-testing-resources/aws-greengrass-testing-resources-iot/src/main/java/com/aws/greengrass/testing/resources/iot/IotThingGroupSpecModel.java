package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateThingGroupRequest;
import software.amazon.awssdk.services.iot.model.CreateThingGroupResponse;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotThingGroupSpecModel extends ResourceSpec<IotClient, IotThingGroup>, IotTaggingMixin {
    String groupName();
    @Nullable
    String parentGroupName();

    @Override
    default IotThingGroupSpec create(IotClient client, AWSResources resources) {
        CreateThingGroupResponse createdGroup = client.createThingGroup(CreateThingGroupRequest.builder()
                .parentGroupName(parentGroupName())
                .thingGroupName(groupName())
                .tags(convertTags(resources.generateResourceTags()))
                .build());
        return IotThingGroupSpec.builder()
                .from(this)
                .resource(IotThingGroup.builder()
                        .groupName(createdGroup.thingGroupName())
                        .groupArn(createdGroup.thingGroupArn())
                        .groupId(createdGroup.thingGroupId())
                        .build())
                .created(true)
                .build();
    }

    @Nullable
    IotThingGroup resource();
}
