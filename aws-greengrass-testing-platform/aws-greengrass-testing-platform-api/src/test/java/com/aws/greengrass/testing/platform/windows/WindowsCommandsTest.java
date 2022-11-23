/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.windows;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WindowsCommandsTest {
    private static final String MOCK_CHILD_PIDS_OF_0 = "ProcessId\n1\n2";
    private static final String MOCK_CHILD_PIDS_OF_1 = "ProcessId\n3";
    private static final String MOCK_NO_CHILD_PIDS = "ProcessId";
    private static final String MOCK_TASK_LIST_OUTPUT = "\"Image Name\",\"PID\",\"Session Name\",\"Session#\",\"Mem Usage\"\n\"greengrass.exe\",\"100\",\"someSessionName\",\"500\",\"16 K\"";

    @Mock
    Device device;

    @Mock
    Path rootDirectory;

    @InjectMocks
    WindowsCommands windowsCommands;

    @BeforeEach
    public void setup() {
        windowsCommands = Mockito.spy(new WindowsCommands(device));
    }

    @Test
    void GIVEN_some_pid_WHEN_descendents_exist_THEN_return_child_pids() {
        Mockito.doReturn(MOCK_CHILD_PIDS_OF_0).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 0 + ") get ProcessId"));
        Mockito.doReturn(MOCK_CHILD_PIDS_OF_1).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 1 + ") get ProcessId"));
        Mockito.doReturn(MOCK_NO_CHILD_PIDS).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 2 + ") get ProcessId"));
        Mockito.doReturn(MOCK_NO_CHILD_PIDS).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 3 + ") get ProcessId"));

        assertEquals(Arrays.asList(0, 1, 2, 3), windowsCommands.findDescendants(0));
        assertEquals(Arrays.asList(1, 3), windowsCommands.findDescendants(1));
    }

    @Test
    void GIVEN_some_pid_WHEN_descendents_do_not_exist_THEN_return_only_parent_pid(){
        Mockito.doReturn(MOCK_NO_CHILD_PIDS).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 2 + ") get ProcessId"));
        Mockito.doReturn(MOCK_NO_CHILD_PIDS).when(windowsCommands).executeToString(CommandInput.of("wmic process where (ParentProcessId=" + 3 + ") get ProcessId"));

        assertEquals(Arrays.asList(2), windowsCommands.findDescendants(2));
        assertEquals(Arrays.asList(3), windowsCommands.findDescendants(3));
    }

    @Test
    void GIVEN_some_tasklist_output_WHEN_looking_for_greengrassPID_THEN_return_greengrassPID() {
        Mockito.doReturn(MOCK_TASK_LIST_OUTPUT).when(windowsCommands).executeToString(CommandInput.of("tasklist /FO csv /FI \"Imagename eq greengrass.exe\""));

        assertEquals(100, windowsCommands.startNucleus(rootDirectory));
    }
}
