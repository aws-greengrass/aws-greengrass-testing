package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface IotCertificateSpecModel extends ResourceSpec<IotClient, IotCertificate> {
    @Value.Default
    default boolean active() {
        return true;
    }

    String thingName();

    @Override
    default IotCertificateSpec create(IotClient client, AWSResources resources) {
        CreateKeysAndCertificateResponse created = client.createKeysAndCertificate(CreateKeysAndCertificateRequest.builder()
                .setAsActive(active())
                .build());
        return IotCertificateSpec.builder()
                .from(this)
                .created(true)
                .resource(IotCertificate.builder()
                        .certificateArn(created.certificateArn())
                        .certificateId(created.certificateId())
                        .certificatePem(created.certificatePem())
                        .build())
                .build();
    }

    @Nullable
    IotCertificate resource();
}
