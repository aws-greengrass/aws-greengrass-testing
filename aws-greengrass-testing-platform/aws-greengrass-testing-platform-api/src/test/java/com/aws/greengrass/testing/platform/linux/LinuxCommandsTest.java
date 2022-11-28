/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.UnixCommands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class LinuxCommandsTest {
    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    LinuxCommands linuxCommands;

    @BeforeEach
    void setup() {
        linuxCommands = Mockito.spy(new LinuxCommands(device, pillboxContext));
    }

    @Test
    void GIVEN_LinuxCommands_object_THEN_object_is_UnixCommands_subclass_object() {
        assertTrue(linuxCommands instanceof UnixCommands);
    }

}
