package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResource;
import org.immutables.value.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;

@TestingModel
@Value.Immutable
interface S3BucketModel extends AWSResource<S3Client> {
    String location();

    String bucketName();

    @Override
    default void remove(S3Client client) {
        client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(bucketName())
                .build());
    }
}
