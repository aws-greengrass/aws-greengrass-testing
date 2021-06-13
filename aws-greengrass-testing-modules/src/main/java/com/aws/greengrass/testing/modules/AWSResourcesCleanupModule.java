package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.CleanupContext;
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

import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(Module.class)
public class AWSResourcesCleanupModule extends AbstractModule {
    private static final String PERSIST_TESTING_RESOURCES = "gg.persist";
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

    @Provides
    @Singleton
    static CleanupContext providesCleanUpContext() {
        /**
         * TODO: switch to SPI so modules can specify their own persistence type and provider
         */
        final Set<PersistMode> modes = Optional.ofNullable(System.getProperty(PERSIST_TESTING_RESOURCES))
                .map(resources -> resources.split("\\s*,\\s*"))
                .map(Arrays::stream)
                .map(stream -> stream.map(PersistMode::fromConfig).collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
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
