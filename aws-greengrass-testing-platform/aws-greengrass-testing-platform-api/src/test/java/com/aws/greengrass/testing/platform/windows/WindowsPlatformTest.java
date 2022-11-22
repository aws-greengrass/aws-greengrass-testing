/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

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
public class WindowsPlatformTest {
    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    WindowsPlatform windowsPlatform;

    @BeforeEach
    public void setup() {
        windowsPlatform = Mockito.spy(new WindowsPlatform(device, pillboxContext));
    }

    @Test
    void GIVEN_WindowsPlatform_object_WHEN_calling_commands_THEN_return_new_MacosCommands_object() {
        assertEquals(WindowsCommands.class, windowsPlatform.commands().getClass());
    }

    @Test
    void GIVEN_WindowsPlatform_object_WHEN_calling_files_THEN_return_PlatformFiles_object() {
        assertTrue(windowsPlatform.files() instanceof PlatformFiles);
    }
    
}
