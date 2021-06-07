package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import javax.annotation.Nullable;

@TestingModel
@Value.Immutable
interface S3ObjectModel extends AWSResource<S3Client> {
    String etag();
    String key();
    String bucket();
    @Nullable
    String versionId();

    @Override
    default void remove(S3Client client) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket())
                .key(key())
                .versionId(versionId())
                .build());
    }
}
