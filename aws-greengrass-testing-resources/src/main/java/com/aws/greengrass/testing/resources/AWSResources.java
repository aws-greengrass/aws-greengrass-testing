package com.aws.greengrass.testing.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class AWSResources implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger(AWSResources.class);
    private final Set<AWSResourceLifecycle> lifecycles;

    public AWSResources(Set<AWSResourceLifecycle> lifecycles) {
        this.lifecycles = lifecycles;
    }

    public static AWSResources loadFromSystem() {
        return new AWSResources(StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        ServiceLoader.load(AWSResourceLifecycle.class).iterator(), Spliterator.DISTINCT), false)
                .map(awsResourceLifecycle -> (AWSResourceLifecycle<?>) awsResourceLifecycle)
                .collect(Collectors.toSet()));
    }

    public <U extends AWSResourceLifecycle> U lifecycle(Class<U> lifecycleType) {
        return lifecycles.stream()
                .filter(lc -> lc.getClass() == lifecycleType)
                .map(lifecycleType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find " + lifecycleType));
    }

    @SuppressWarnings("unchecked")
    public <C, U extends ResourceSpec<C, R>, R extends AWSResource<C>> U create(U spec) {
        return find((Class<U>) spec.getClass())
                .map(lc -> lc.create(spec, this))
                .orElseThrow(() -> new IllegalArgumentException("Could not find lifecycle for " + spec.getClass()));
    }

    public <C, U extends ResourceSpec<C, R>, R extends AWSResource<C>> Stream<U> trackingSpecs(Class<U> specClass) {
        return find(specClass)
                .map(lc -> lc.trackingSpecs(specClass))
                .orElseGet(Stream::empty);
    }

    @SuppressWarnings("unchecked")
    private <C, S extends ResourceSpec<C, R>, R extends AWSResource<C>> Optional<AWSResourceLifecycle<C>> find(Class<S> specClass) {
        return lifecycles.stream()
                .filter(lc -> lc.getSupportedSpecs().contains(specClass))
                .findFirst()
                .map(lc -> (AWSResourceLifecycle<C>) lc);
    }

    @Override
    public void close() {
        for (AWSResourceLifecycle<?> lifecycle : lifecycles) {
            try {
                lifecycle.close();
            } catch (IOException ie) {
                LOGGER.error("Failed to clean resources from {}", lifecycle, ie);
            }
        }
    }
}
