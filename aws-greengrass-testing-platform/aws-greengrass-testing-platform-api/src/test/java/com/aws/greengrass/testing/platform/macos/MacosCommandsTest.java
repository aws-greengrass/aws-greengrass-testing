/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MacosCommandsTest {

    private static final String MOCK_PIDS = " PPID   PID\n0 1\n2 3\n2 4\n4 5";

    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    MacosCommands macosCommands;

    @BeforeEach
    public void setup() {
        macosCommands = Mockito.spy(new MacosCommands(device, pillboxContext));
    } 

    @Test
    void GIVEN_some_ps_output_WHEN_child_pids_exist_THEN_child_pids_are_returned() {
        Mockito.doReturn(MOCK_PIDS).when(macosCommands).executeToString(CommandInput.builder().line("ps a -o ppid,pid").build());

        assertEquals(Arrays.asList(0, 1), macosCommands.findDescendants(0));
        assertEquals(Arrays.asList(2, 4, 3, 5), macosCommands.findDescendants(2));
    }

    @Test
    void GIVEN_some_ps_output_WHEN_child_pids_do_not_exist_THEN_only_parent_pid_returned() {
        Mockito.doReturn(MOCK_PIDS).when(macosCommands).executeToString(CommandInput.builder().line("ps a -o ppid,pid").build());

        assertEquals(Arrays.asList(6), macosCommands.findDescendants(6));
    }
    
}
