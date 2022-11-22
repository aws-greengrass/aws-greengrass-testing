/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.PlatformFiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LinuxPlatformTest {
    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    LinuxPlatform linuxPlatform;

    @BeforeEach
    public void setup() {
        linuxPlatform = Mockito.spy(new LinuxPlatform(device, pillboxContext));
    }

    @Test
    void GIVEN_LinuxPlatform_object_WHEN_calling_commands_THEN_return_new_LinuxCommands_object() {
        assertEquals(LinuxCommands.class, linuxPlatform.commands().getClass());
    }

    @Test
    void GIVEN_LinuxPlatform_object_WHEN_calling_files_THEN_return_PlatformFiles_object() {
        assertTrue(linuxPlatform.files() instanceof PlatformFiles);
    }
}
