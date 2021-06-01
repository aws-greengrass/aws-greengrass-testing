package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateStatus;
import software.amazon.awssdk.services.iot.model.DeleteCertificateRequest;
import software.amazon.awssdk.services.iot.model.KeyPair;
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest;

@TestingModel
@Value.Immutable
interface IotCertificateModel extends AWSResource<IotClient> {
    String certificateArn();
    String certificateId();
    String certificatePem();
    KeyPair keyPair();

    @Override
    default void remove(IotClient client) {
        client.updateCertificate(UpdateCertificateRequest.builder()
                .newStatus(CertificateStatus.INACTIVE)
                .certificateId(certificateId())
                .build());
        client.deleteCertificate(DeleteCertificateRequest.builder()
                .certificateId(certificateId())
                .forceDelete(true)
                .build());
    }
}
