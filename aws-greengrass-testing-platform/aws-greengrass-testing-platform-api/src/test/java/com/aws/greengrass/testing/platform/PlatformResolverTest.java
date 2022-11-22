/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.exception.PlatformResolutionException;
import com.aws.greengrass.testing.platform.linux.LinuxPlatform;
import com.aws.greengrass.testing.platform.macos.MacosPlatform;
import com.aws.greengrass.testing.platform.windows.WindowsPlatform;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class PlatformResolverTest {
    
    private static final Map<String,Integer> linuxMap = new HashMap<String, Integer>() {{
        put("linux", 10);
    }};

    private static final Map<String,Integer> macosMap = new HashMap<String, Integer>() {{
        put("macos", 20);
    }};

    @Mock
    PlatformOS platformOS;

    @Mock
    Device device;

    @Mock
    PillboxContext pillboxContext;

    @InjectMocks
    PlatformResolver platformResolver;

    @BeforeEach
    public void setup() {
        platformResolver = Mockito.spy(new PlatformResolver(device, pillboxContext));
        Mockito.doReturn(platformOS).when(device).platform();
    }

    @Test
    void GIVEN_linux_WHEN_resolving_os_THEN_return_LinuxPlatform_object() {
        Mockito.doReturn(false).when(platformOS).isWindows();
        Mockito.doReturn(false).when(device).exists(Mockito.anyString());
        Mockito.doReturn("").when(device).executeToString(CommandInput.builder().line("sh").addArgs("-c", "uname -a").build());
        Mockito.doReturn(true).when(device).exists("/proc");

        assertEquals(LinuxPlatform.class, platformResolver.resolve().getClass());
    }

    @Test
    void GIVEN_macos_WHEN_resolving_os_THEN_return_MacosPlatform_object() {
        Mockito.doReturn(false).when(platformOS).isWindows();
        Mockito.doReturn(false).when(device).exists(Mockito.anyString());
        Mockito.doReturn("darwin Darwin").when(device).executeToString(CommandInput.builder().line("sh").addArgs("-c", "uname -a").build());

        assertEquals(MacosPlatform.class, platformResolver.resolve().getClass());
    }
    @Test
    void GIVEN_device_has_windows_platformOS_WHEN_resolving_os_THEN_return_WindowsPlatform_object() {
        Mockito.doReturn(true).when(platformOS).isWindows();

        assertEquals(WindowsPlatform.class, platformResolver.resolve().getClass());
    }

    @Test
    void GIVEN_unsupported_os_WHEN_resolving_os_THEN_throw_exception() {
        Mockito.doReturn(false).when(platformOS).isWindows();
        Mockito.doReturn(true).when(device).exists(Mockito.anyString());
        Mockito.doReturn(false).when(device).exists("/proc");
        Mockito.doReturn("ubuntu raspbian qnx cygwin freebsd solaris").when(device).executeToString(CommandInput.builder().line("sh").addArgs("-c", "uname -a").build());

        assertThrows(PlatformResolutionException.class, () -> platformResolver.resolve());
    }
}
