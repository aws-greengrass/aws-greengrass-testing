/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.launcher.utils.CucumberReportUtils;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;

import javax.inject.Singleton;

@AutoService(Module.class)
public class TestLauncherModule extends AbstractModule {

    @Singleton
    @ProvidesIntoSet
    static ParameterValues providesTestLauncherValues() {
        return new TestLauncherParameterValues();
    }

}
