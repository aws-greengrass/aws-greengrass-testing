/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.s3.S3BucketSpec;
import com.aws.greengrass.testing.resources.s3.S3Lifecycle;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;

public class S3StepsTest {
    private static final String MOCK_ID_FOR_BUCKET = "mock_id_for_bucket";
    private static final String MOCK_BUCKET_NAME = "mock_bucket_name";
    private static final String MOCK_S3_OBJ_KEY = "mock_S3_obj_key";

    TestId testId = Mockito.mock(TestId.class);

    AWSResources resources = Mockito.mock(AWSResources.class);

    WaitSteps waits = new WaitSteps(TimeoutMultiplier.builder().multiplier(1).build());

    S3Lifecycle s3 = Mockito.mock(S3Lifecycle.class);

    @InjectMocks
    S3Steps s3Steps = Mockito.spy(new S3Steps(resources, testId, waits));

    @BeforeEach
    public void setup() {
        Mockito.doReturn(MOCK_ID_FOR_BUCKET).when(testId).idFor(Mockito.anyString());
    }

    @Test
    void GIVEN_a_test_case_wants_to_create_s3_bucket_WHEN_create_s3_bucket_is_called_with_bucket_name_THEN_bucket_is_created() {
        Mockito.doReturn(S3BucketSpec.builder().bucketName(MOCK_BUCKET_NAME).build()).when(resources).create(S3BucketSpec.builder()
                .bucketName(MOCK_BUCKET_NAME)
                .build());
        s3Steps.createTestingBucket();
        Mockito.verify(s3Steps, Mockito.times(1)).createS3Bucket(Mockito.anyString());
    }

    @Test
    void GIVEN_a_test_case_wants_to_check_if_a_bucket_contains_key_WHEN_bucket_contains_key_is_called_with_valid_inputs_THEN_it_can_check_the_existence() throws InterruptedException {
        Mockito.doReturn(s3).when(resources).lifecycle(S3Lifecycle.class);
        Mockito.doReturn(true).when(s3).objectExists(Mockito.any(), Mockito.any());
        assertDoesNotThrow(() -> s3Steps.bucketContainsKey(MOCK_S3_OBJ_KEY, 10, "SECONDS"));
    }

}
