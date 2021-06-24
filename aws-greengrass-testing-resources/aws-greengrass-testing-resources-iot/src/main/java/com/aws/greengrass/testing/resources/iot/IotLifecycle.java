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
import software.amazon.awssdk.services.iot.model.ListThingsInThingGroupRequest;
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

    private String endpointType(String endpoint) {
        return client.describeEndpoint(DescribeEndpointRequest.builder()
                .endpointType(endpoint)
                .build())
                .endpointAddress();
    }
}
