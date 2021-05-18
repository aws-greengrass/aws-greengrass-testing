package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DeleteThingRequest;
import software.amazon.awssdk.services.iot.model.DetachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest;

import javax.annotation.Nullable;
import java.util.List;

@TestingModel
@Value.Immutable
interface IotThingModel extends AWSResource<IotClient> {
    String thingName();
    String thingId();
    String thingArn();
    List<IotThingGroup> thingGroups();
    @Nullable
    IotCertificate certificate();

    @Override
    default void remove(IotClient client) {
        client.listThingPrincipals(ListThingPrincipalsRequest.builder().thingName(thingName()).build()).principals().forEach(p -> {
            client.detachThingPrincipal(DetachThingPrincipalRequest.builder()
                    .thingName(thingName())
                    .principal(p)
                    .build());
        });
        client.deleteThing(DeleteThingRequest.builder()
                .thingName(thingName())
                .build());
    }
}
