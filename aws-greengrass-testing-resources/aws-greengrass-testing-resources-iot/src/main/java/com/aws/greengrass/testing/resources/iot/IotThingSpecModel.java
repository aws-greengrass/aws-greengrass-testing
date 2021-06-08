package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.CreateThingResponse;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@TestingModel
@Value.Immutable
interface IotThingSpecModel extends ResourceSpec<IotClient, IotThing> {
    @Nullable
    Set<IotThingGroupSpec> thingGroups();

    String thingName();

    IotRoleAliasSpec roleAliasSpec();

    @Nullable
    IotPolicySpec policySpec();

    @Override
    default IotThingSpec create(IotClient client, AWSResources resources) {
        Set<IotThingGroupSpec> createdGroups = Optional.ofNullable(thingGroups())
                .map(groupSpecs -> groupSpecs.stream().map(resources::create).collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);

        CreateThingResponse createdThing = client.createThing(CreateThingRequest.builder()
                .thingName(thingName())
                .build());

        IotCertificate certificate = null;
        if (createCertificate()) {
            certificate = resources.create(IotCertificateSpec.builder()
                    .thingName(thingName())
                    .policy(resources.create(policySpec()))
                    .build())
                    .resource();
        }

        return IotThingSpec.builder()
                .from(this)
                .roleAliasSpec(resources.create(roleAliasSpec()))
                .resource(IotThing.builder()
                        .thingName(thingName())
                        .thingArn(createdThing.thingArn())
                        .thingId(createdThing.thingId())
                        .certificate(certificate)
                        .addAllThingGroups(createdGroups.stream()
                                .map(IotThingGroupSpec::resource)
                                .collect(Collectors.toSet()))
                        .build())
                .created(true)
                .build();
    }

    @Value.Default
    default boolean createCertificate() {
        return true;
    }

    @Nullable
    IotThing resource();
}
