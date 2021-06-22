package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.CreateThingResponse;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotThingSpecModel extends ResourceSpec<IotClient, IotThing>, IotTaggingMixin {
    @Nullable
    Set<IotThingGroupSpec> thingGroups();

    String thingName();

    @Nullable
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

        IotRoleAliasSpec updatedRoleAlias = null;
        IotPolicySpec assumeRolePolicy = null;
        IotCertificate certificate = null;

        if (roleAliasSpec() != null) {
            updatedRoleAlias = resources.create(roleAliasSpec());
            assumeRolePolicy = resources.create(IotPolicySpec.builder()
                    .policyName(policySpec().policyName() + "-credentials")
                    .policyDocument("{\"Version\":\"2012-10-17\",\"Statement\":[{"
                            + "\"Effect\":\"Allow\","
                            + "\"Action\":\"iot:AssumeRoleWithCertificate\","
                            + "\"Resource\":\"" + updatedRoleAlias.resource().roleAliasArn() + "\"}]}")
                    .build());
        }

        if (createCertificate()) {
            certificate = resources.create(IotCertificateSpec.builder()
                    .thingName(thingName())
                    .policy(resources.create(policySpec()))
                    .build())
                    .resource();
            if (assumeRolePolicy != null) {
                client.attachPolicy(AttachPolicyRequest.builder()
                        .policyName(assumeRolePolicy.policyName())
                        .target(certificate.certificateArn())
                        .build());
            }
        }

        return IotThingSpec.builder()
                .from(this)
                .roleAliasSpec(updatedRoleAlias)
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
