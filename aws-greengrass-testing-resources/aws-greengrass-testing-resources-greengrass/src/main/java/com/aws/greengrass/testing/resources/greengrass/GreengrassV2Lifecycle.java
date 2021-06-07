package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVisibilityScope;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentVersionsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentsRequest;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentVersionsIterable;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentsIterable;

import javax.inject.Inject;

@AutoService(AWSResourceLifecycle.class)
public class GreengrassV2Lifecycle extends AbstractAWSResourceLifecycle<GreengrassV2Client> {
    @Inject
    public GreengrassV2Lifecycle(GreengrassV2Client client) {
        super(client, GreengrassComponentSpec.class, GreengrassDeploymentSpec.class);
    }

    public GreengrassV2Lifecycle() {
        this(GreengrassV2Client.create());
    }

    public ListComponentVersionsIterable listComponentVersions(String arn) {
        return client.listComponentVersionsPaginator(ListComponentVersionsRequest.builder()
                .arn(arn)
                .build());
    }

    public ListComponentsIterable listComponents(ComponentVisibilityScope scope) {
        return client.listComponentsPaginator(ListComponentsRequest.builder()
                .scope(scope)
                .build());
    }
}
