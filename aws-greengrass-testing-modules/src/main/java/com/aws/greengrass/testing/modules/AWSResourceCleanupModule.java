package com.aws.greengrass.testing.modules;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

@AutoService(Module.class)
public class AWSResourceCleanupModule extends AbstractModule {
    private static final Logger LOGGER = LogManager.getLogger(AWSResourceCleanupModule.class);

    private static class CleanupRunnable implements Runnable {
        final Set<Closeable> closers;

        CleanupRunnable(final Set<Closeable> closers) {
            this.closers = closers;
        }

        @Override
        public void run() {
            LOGGER.info("Cleaning up orphaned resources");
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
                LOGGER.info("Provisioning {} for cleanup", instance.getClass());
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
                LOGGER.debug("{} removed a resource", methodInvocation.getMethod().getDeclaringClass());
            }
            return methodInvocation.proceed();
        }
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
