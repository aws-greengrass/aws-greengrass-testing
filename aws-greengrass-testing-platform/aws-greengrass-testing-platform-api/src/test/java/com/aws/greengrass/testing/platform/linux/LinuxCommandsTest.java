/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.linux;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.PillboxContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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

}
