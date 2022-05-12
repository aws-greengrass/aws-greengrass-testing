/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iot;


import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DescribeEndpointRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;
import software.amazon.awssdk.services.iot.model.DescribeThingResponse;
import software.amazon.awssdk.services.iot.model.ListThingGroupsForThingRequest;
import software.amazon.awssdk.services.iot.model.ListThingsInThingGroupRequest;
import software.amazon.awssdk.services.iot.paginators.ListThingGroupsForThingIterable;
import software.amazon.awssdk.services.iot.paginators.ListThingsInThingGroupIterable;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class IotLifecycle extends AbstractAWSResourceLifecycle<IotClient> {
    private static final String CREDENTIALS_ENDPOINT = "iot:CredentialProvider";
    private static final String DATA_ENDPOINT = "iot:Data-ATS";

    /**
     * Create a {@link IotLifecycle} with a customized {@link IotClient}.
     *
     * @param client Customized {@link IotClient}
     */
    @Inject
    public IotLifecycle(final IotClient client) {
        super(client,
                IotThingSpec.class,
                IotCertificateSpec.class,
                IotThingGroupSpec.class,
                IotRoleAliasSpec.class,
                IotPolicySpec.class);
    }

    public IotLifecycle() {
        this(IotClient.create());
    }

    public String credentialsEndpoint() {
        return endpointType(CREDENTIALS_ENDPOINT);
    }

    public String dataEndpoint() {
        return endpointType(DATA_ENDPOINT);
    }

    /**
     * Operation to pull a thing resource by it's canonical name.
     * <strong>Note</strong>: This operation only exists in the absence of an {@link AWSResourceLifecycle}::load
     * method. Proper resource loading means test resources will be tracked for a scenario.
     *
     * @param thingName canonical name of the AWS IoT Thing
     * @return
     */
    public IotThing thingByThingName(String thingName) {
        // TODO: This whole method is removed in favor of a "resource spec loading" mechanism
        DescribeThingResponse response = client.describeThing(DescribeThingRequest.builder()
                .thingName(thingName)
                .build());
        return IotThing.builder()
                .thingArn(response.thingArn())
                .thingId(response.thingId())
                .thingName(response.thingName())
                .build();
    }

    /**
     * List things for a group by name.
     *
     * @param thingGroupName String thing group name
     * @return
     */
    public ListThingsInThingGroupIterable listThingsForGroup(String thingGroupName) {
        return client.listThingsInThingGroupPaginator(ListThingsInThingGroupRequest.builder()
                .recursive(true)
                .thingGroupName(thingGroupName)
                .build());
    }

    /**
     * List the thing groups for a thing.
     * @param thingName name of the thing
     * @return
     */
    public ListThingGroupsForThingIterable listThingGroupsForAThing(String thingName) {
        return client.listThingGroupsForThingPaginator(ListThingGroupsForThingRequest.builder()
                .thingName(thingName)
                .build());
    }

    private String endpointType(String endpoint) {
        return client.describeEndpoint(DescribeEndpointRequest.builder()
                .endpointType(endpoint)
                .build())
                .endpointAddress();
    }
}
