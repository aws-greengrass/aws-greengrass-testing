/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.api.model.PillboxContext;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RemoteFilesTest {
    private Path filePath = Paths.get("/tmp/dummyDirectory");

    @Mock
    PlatformOS host;

    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    RemoteFiles remoteFiles;

    @BeforeEach
    public void setup() {
        remoteFiles = Mockito.spy(new RemoteFiles(device, pillboxContext));
        Mockito.doReturn(filePath).when(pillboxContext).onDevice();
    }

    @Test
    void GIVEN_a_filepath_WHEN_making_directories_THEN_filepath_should_exist() {
        remoteFiles.makeDirectories(filePath);
    }

}
