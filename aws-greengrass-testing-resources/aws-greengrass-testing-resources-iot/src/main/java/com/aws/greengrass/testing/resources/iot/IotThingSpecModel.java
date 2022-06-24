/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.AddThingToThingGroupRequest;
import software.amazon.awssdk.services.iot.model.AddThingToThingGroupResponse;
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest;
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest;
import software.amazon.awssdk.services.iot.model.CreateThingRequest;
import software.amazon.awssdk.services.iot.model.CreateThingResponse;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingResponse;

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

    @Nullable
    IotCertificateSpec certificateSpec();

    @Override
    default IotThingSpec create(IotClient client, AWSResources resources) {
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

        Set<IotThingGroupSpec> createdGroups = Optional.ofNullable(thingGroups())
                .map(groupSpecs -> groupSpecs.stream().map(resources::create).collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);

        CreateThingResponse createdThing;

        DescribeThingResponse describeThingResponse = client.describeThing(DescribeThingRequest.builder()
                .thingName(thingName())
                .build());

        //If thing doesn't exist
        if (describeThingResponse.sdkHttpResponse().statusCode() == 404) {
            System.out.println("Thing not found, creating thing..");
            createdThing = client.createThing(CreateThingRequest.builder()
                    .thingName(thingName())
                    .build());

            createdGroups.stream().forEach(g -> {
                client.addThingToThingGroup(AddThingToThingGroupRequest.builder()
                        .thingArn(createdThing.thingArn())
                        .thingGroupName(g.groupName())
                        .build());
            });
        } else {
            System.out.println("Thing found with thingarn " + describeThingResponse.thingArn());

            createdGroups.stream().forEach(g -> {
                client.addThingToThingGroup(AddThingToThingGroupRequest.builder()
                        .thingArn(describeThingResponse.thingArn())
                        .thingGroupName(g.groupName())
                        .build());
                System.out.println("Adding thing to thing group " + g.groupName());
            });
            //Creating dummy ThingSpec to satisfy method return argument
            return IotThingSpec.builder()
                    .from(this)
                    .resource(IotThing.builder()
                            .thingName("")
                            .thingArn("")
                            .thingId("")
                            .addAllThingGroups(createdGroups.stream()
                                    .map(IotThingGroupSpec::resource)
                                    .collect(Collectors.toSet()))
                            .build())
                    .created(true)
                    .build();
        }


        String certificateArn = null;
        if (createCertificate()) {
            certificate = resources.create(IotCertificateSpec.builder()
                    .thingName(thingName())
                    .policy(resources.create(policySpec()))
                    .csr(certificateSpec().csr())
                    .build())
                    .resource();
           certificateArn = certificate.certificateArn();
        } else if (certificateSpec() != null && certificateSpec().existingArn() != null) {
            client.attachThingPrincipal(AttachThingPrincipalRequest.builder()
                    .thingName(thingName())
                    .principal(certificateSpec().existingArn())
                    .build());
            certificateArn = certificateSpec().existingArn();
            if (policySpec() != null) {
                client.attachPolicy(AttachPolicyRequest.builder()
                        .policyName(policySpec().policyName())
                        .target(certificateArn)
                        .build());
            }
        }

        if (assumeRolePolicy != null) {
            client.attachPolicy(AttachPolicyRequest.builder()
                    .policyName(assumeRolePolicy.policyName())
                    .target(certificateArn)
                    .build());
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