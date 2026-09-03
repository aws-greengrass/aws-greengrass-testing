/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.greengrass;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.DeleteCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.GetCoreDeviceResponse;
import software.amazon.awssdk.services.greengrassv2.model.GreengrassV2Exception;
import software.amazon.awssdk.services.greengrassv2.model.ResourceNotFoundException;

class GreengrassCoreDeviceModelTest {
    private static final String THING_NAME = "gg-test-ggc-thing";

    private final GreengrassV2Client client = Mockito.mock(GreengrassV2Client.class);

    // deleteDrainWaitMillis(0) keeps the test fast; production defaults to 5s.
    private final GreengrassCoreDevice coreDevice = GreengrassCoreDevice.builder()
            .thingName(THING_NAME)
            .deleteDrainWaitMillis(0L)
            .build();

    @Test
    void GIVEN_delete_sticks_WHEN_remove_THEN_deletes_once_and_verifies_gone() {
        Mockito.when(client.getCoreDevice(Mockito.any(GetCoreDeviceRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().build());

        coreDevice.remove(client);

        Mockito.verify(client, Mockito.times(1))
                .deleteCoreDevice(Mockito.any(DeleteCoreDeviceRequest.class));
        Mockito.verify(client, Mockito.times(1))
                .getCoreDevice(Mockito.any(GetCoreDeviceRequest.class));
    }

    @Test
    void GIVEN_late_status_resurrects_record_WHEN_remove_THEN_retries_delete_until_gone() {
        // First verify still finds the (resurrected) record; second verify confirms it is gone.
        Mockito.when(client.getCoreDevice(Mockito.any(GetCoreDeviceRequest.class)))
                .thenReturn(GetCoreDeviceResponse.builder().coreDeviceThingName(THING_NAME).build())
                .thenThrow(ResourceNotFoundException.builder().build());

        coreDevice.remove(client);

        Mockito.verify(client, Mockito.times(2))
                .deleteCoreDevice(Mockito.any(DeleteCoreDeviceRequest.class));
        Mockito.verify(client, Mockito.times(2))
                .getCoreDevice(Mockito.any(GetCoreDeviceRequest.class));
    }

    @Test
    void GIVEN_record_persists_WHEN_remove_THEN_stops_after_max_attempts() {
        Mockito.when(client.getCoreDevice(Mockito.any(GetCoreDeviceRequest.class)))
                .thenReturn(GetCoreDeviceResponse.builder().coreDeviceThingName(THING_NAME).build());

        coreDevice.remove(client);

        Mockito.verify(client, Mockito.times(3))
                .deleteCoreDevice(Mockito.any(DeleteCoreDeviceRequest.class));
    }

    @Test
    void GIVEN_verify_hits_transient_error_WHEN_remove_THEN_retries_delete() {
        // Simulate a transient service exception on the first verify (e.g. throttling),
        // then confirm the record is gone on the second attempt.
        Mockito.when(client.getCoreDevice(Mockito.any(GetCoreDeviceRequest.class)))
                .thenThrow(GreengrassV2Exception.builder().message("transient").build())
                .thenThrow(ResourceNotFoundException.builder().build());

        coreDevice.remove(client);

        Mockito.verify(client, Mockito.times(2))
                .deleteCoreDevice(Mockito.any(DeleteCoreDeviceRequest.class));
        Mockito.verify(client, Mockito.times(2))
                .getCoreDevice(Mockito.any(GetCoreDeviceRequest.class));
    }
}
