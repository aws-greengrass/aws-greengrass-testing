package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.api.model.TestingModel;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import org.immutables.value.Value;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@TestingModel
@Value.Immutable
interface S3ObjectSpecModel extends ResourceSpec<S3Client, S3Object> {
    String key();
    String bucket();
    RequestBody content();

    @Override
    default S3ObjectSpec create(S3Client client, AWSResources resources) {
        final PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket())
                .key(key())
                .build();
        final PutObjectResponse putResponse = client.putObject(putRequest, content());

        return S3ObjectSpec.builder()
                .from(this)
                .created(true)
                .resource(S3Object.builder()
                        .bucket(bucket())
                        .key(key())
                        .etag(putResponse.eTag())
                        .versionId(putResponse.versionId())
                        .build())
                .build();
    }
}