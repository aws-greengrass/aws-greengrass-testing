/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.platform.PlatformResolver;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.cucumber.guice.ScenarioScoped;

@AutoService(Module.class)
public class PlatformModule extends AbstractModule {
    @Provides
    @ScenarioScoped
    static Platform providesPlatform(final Device device) {
        return new PlatformResolver(device).resolve();
    }
}
