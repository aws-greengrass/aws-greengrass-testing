package com.aws.greengrass.testing.resources.greengrass;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.CancelDeploymentRequest;
import software.amazon.awssdk.services.greengrassv2.model.DeleteCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GreengrassV2Exception;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@TestingModel
@Value.Immutable
interface GreengrassDeploymentModel extends AWSResource<GreengrassV2Client> {
    static Logger LOGGER = LoggerFactory.getLogger(GreengrassDeployment.class);

    String deploymentId();

    @Nullable
    List<String> thingNames();

    @Override
    default void remove(GreengrassV2Client client) {
        client.cancelDeployment(CancelDeploymentRequest.builder()
                .deploymentId(deploymentId())
                .build());

        Optional.ofNullable(thingNames()).ifPresent(thingNames -> {
            thingNames.forEach(thingName -> {
                try {
                    client.deleteCoreDevice(DeleteCoreDeviceRequest.builder()
                            .coreDeviceThingName(thingName)
                            .build());
                } catch (GreengrassV2Exception e) {
                    LOGGER.info("Could not delete core device {}", thingName);
                }
            });
        });
    }
}
