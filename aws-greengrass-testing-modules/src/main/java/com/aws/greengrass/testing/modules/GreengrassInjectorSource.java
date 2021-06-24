/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.cucumber.guice.CucumberModules;
import io.cucumber.guice.InjectorSource;

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GreengrassInjectorSource implements InjectorSource {

    protected Spliterator<Module> providedModules() {
        return Spliterators.spliteratorUnknownSize(ServiceLoader.load(Module.class).iterator(), Spliterator.NONNULL);
    }

    private Module[] obtainSystemModules() {
        return Stream.concat(defaultModules().stream(), StreamSupport.stream(providedModules(), false))
                .toArray(Module[]::new);
    }

    protected List<Module> defaultModules() {
        return Arrays.asList(CucumberModules.createScenarioModule());
    }

    @Override
    public Injector getInjector() {
        return Guice.createInjector(obtainSystemModules());
    }
}
