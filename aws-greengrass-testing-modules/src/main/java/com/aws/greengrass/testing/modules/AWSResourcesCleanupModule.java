/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.api.model.ParameterValue;
import com.aws.greengrass.testing.api.model.PersistMode;
import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@AutoService(Module.class)
public class AWSResourcesCleanupModule extends AbstractModule {
    private static final Logger LOGGER = LogManager.getLogger(AWSResourcesCleanupModule.class);

    private static class CleanupRunnable implements Runnable {
        final Set<Closeable> closers;

        CleanupRunnable(final Set<Closeable> closers) {
            this.closers = closers;
        }

        @Override
        public void run() {
            LOGGER.debug("Cleaning up orphaned resources: {}", closers);
            for (Closeable closer : closers) {
                try {
                    closer.close();
                    LOGGER.info("Cleaned up {}", closer);
                } catch (IOException ie) {
                    LOGGER.error("Failed to clean up {}", closer, ie);
                }
            }
        }
    }

    private static class CleanupProvisioner implements ProvisionListener {
        final Set<Closeable> closers;

        CleanupProvisioner(final Set<Closeable> closers) {
            this.closers = closers;
        }

        @Override
        public <T> void onProvision(ProvisionInvocation<T> provisionInvocation) {
            final T instance = provisionInvocation.provision();
            if (instance instanceof Closeable && closers.add((Closeable) instance)) {
                LOGGER.debug("Provisioning {} for cleanup", instance.getClass());
            }
        }
    }

    private static class CleanupInterceptor implements MethodInterceptor {
        final Set<Closeable> closers;

        CleanupInterceptor(final Set<Closeable> closers) {
            this.closers = closers;
        }

        @Override
        public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            LOGGER.debug("Cleanup interceptor found {}", methodInvocation.getMethod());
            if (methodInvocation.getMethod().getName().equals("close") && closers.remove(methodInvocation.getThis())) {
                LOGGER.debug("{} removed a resource", methodInvocation.getThis().getClass().getSimpleName());
            }
            return methodInvocation.proceed();
        }
    }

    static Set<PersistMode> fromParameterValues(ParameterValues parameterValues, String key) {
        // TODO: switch to SPI so modules can specify their own persistence type and provider

        //Maps the value "yes" to INSTALLED_SOFTWARE
        //TODO: use new enum PREINSTALLED_GREENGRASS
        if (key.equals(ModuleParameters.RUNTIME_TESTING_RESOURCES)) {
            return parameterValues.getString(key)
                    .map(resources -> resources.split("\\s*,\\s*"))
                    .map(Arrays::stream)
                    .map(stream -> stream.map(s -> s.equalsIgnoreCase("yes") ? "INSTALLED_SOFTWARE" : s))
                    .map(stream -> stream.filter(PersistMode::validPersistMode))
                    .map(stream -> stream.map(PersistMode::fromConfig).collect(Collectors.toSet()))
                    .orElseGet(Collections::emptySet);
        }

        return parameterValues.getString(key)
                .map(resources -> resources.split("\\s*,\\s*"))
                .map(Arrays::stream)
                .map(stream -> stream.map(PersistMode::fromConfig).collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }

    @Provides
    @Singleton
    static InitializationContext providesInitializationContext(final ParameterValues parameterValues) {
        return InitializationContext.fromModes(fromParameterValues(parameterValues,
                ModuleParameters.RUNTIME_TESTING_RESOURCES));
    }

    @Provides
    @Singleton
    static CleanupContext providesCleanUpContext(
            final InitializationContext initializationContext,
            final ParameterValues parameterValues) {
        final Set<PersistMode> modes = new HashSet<>(initializationContext.persistModes());
        modes.addAll(fromParameterValues(parameterValues, ModuleParameters.PERSIST_TESTING_RESOURCES));
        LOGGER.debug("Using persist modes: {}", modes);
        return CleanupContext.fromModes(modes);
    }

    @Override
    protected void configure() {
        final Set<Closeable> closers = Sets.newConcurrentHashSet();
        final MethodInterceptor cleanup = new CleanupInterceptor(closers);
        Runtime.getRuntime().addShutdownHook(new Thread(new CleanupRunnable(closers)));
        bindListener(Matchers.any(), new CleanupProvisioner(closers));
        bindInterceptor(Matchers.subclassesOf(Closeable.class), Matchers.any(), cleanup);
    }
}