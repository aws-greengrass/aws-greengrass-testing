package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.resources.AWSResourceLifecycle;
import com.aws.greengrass.testing.resources.AbstractAWSResourceLifecycle;
import com.google.auto.service.AutoService;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.ComponentVisibilityScope;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceResponse;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetDeploymentResponse;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentVersionsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListComponentsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListEffectiveDeploymentsRequest;
import software.amazon.awssdk.services.greengrassv2.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentVersionsIterable;
import software.amazon.awssdk.services.greengrassv2.paginators.ListComponentsIterable;
import software.amazon.awssdk.services.greengrassv2.paginators.ListEffectiveDeploymentsIterable;

import javax.inject.Inject;
import java.util.Optional;

@AutoService(AWSResourceLifecycle.class)
public class GreengrassV2Lifecycle extends AbstractAWSResourceLifecycle<GreengrassV2Client> {
    @Inject
    public GreengrassV2Lifecycle(GreengrassV2Client client) {
        super(client, GreengrassComponentSpec.class, GreengrassDeploymentSpec.class);
    }

    public GreengrassV2Lifecycle() {
        this(GreengrassV2Client.create());
    }

    public Optional<GetCoreDeviceResponse> coreDevice(String thingName) {
        try {
            return Optional.ofNullable(client.getCoreDevice(GetCoreDeviceRequest.builder()
                    .coreDeviceThingName(thingName)
                    .build()));
        } catch (ResourceNotFoundException nfe) {
            return Optional.empty();
        }
    }

    public ListEffectiveDeploymentsIterable listDeviceDeployments(String thingName) {
        return client.listEffectiveDeploymentsPaginator(ListEffectiveDeploymentsRequest.builder()
                .coreDeviceThingName(thingName)
                .build());
    }

    public GetDeploymentResponse deployment(String deploymentId) {
        return client.getDeployment(GetDeploymentRequest.builder()
                .deploymentId(deploymentId)
                .build());
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
