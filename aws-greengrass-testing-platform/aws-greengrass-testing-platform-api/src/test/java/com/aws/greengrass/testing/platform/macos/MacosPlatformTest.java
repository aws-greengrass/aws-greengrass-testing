/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.PlatformFiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MacosPlatformTest {
    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    MacosPlatform macosPlatform;

    @BeforeEach
    public void setup() {
        macosPlatform = Mockito.spy(new MacosPlatform(device, pillboxContext));
    }

    @Test
    void GIVEN_MacosPlatform_object_WHEN_calling_commands_THEN_return_new_MacosCommands_object() {
        assertEquals(MacosCommands.class, macosPlatform.commands().getClass());
    }

    @Test
    void GIVEN_MacosPlatform_object_WHEN_calling_files_THEN_return_PlatformFiles_object() {
        assertTrue(macosPlatform.files() instanceof PlatformFiles);
    }
}
