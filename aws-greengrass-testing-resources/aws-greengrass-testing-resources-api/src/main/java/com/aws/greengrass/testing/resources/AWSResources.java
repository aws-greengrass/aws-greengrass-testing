package com.aws.greengrass.testing.resources;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.TestId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AWSResources implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger(AWSResources.class);
    private final Set<AWSResourceLifecycle> lifecycles;
    private final CleanupContext cleanupContext;
    private final TestId testId;

    public AWSResources(
            Set<AWSResourceLifecycle> lifecycles,
            CleanupContext cleanupContext,
            TestId testId) {
        this.lifecycles = lifecycles;
        this.cleanupContext = cleanupContext;
        this.testId = testId;
    }

    public AWSResources(Set<AWSResourceLifecycle> lifecycles) {
        this(lifecycles,
                CleanupContext.builder().build(),
                TestId.builder()
                        .id(UUID.randomUUID().toString())
                        .build());
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
                .peek(lc -> LOGGER.debug("Available lifecycle {}", lc))
                .filter(lc -> lifecycleType.isAssignableFrom(lc.getClass()))
                .map(lifecycleType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find " + lifecycleType));
    }

    public Map<String, String> generateResourceTags() {
        return Collections.unmodifiableMap(new HashMap<String, String>() {{
            put("Testing", "GG");
            put("TestId", testId.id());
        }});
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AWSResources that = (AWSResources) o;
        return Objects.equals(lifecycles, that.lifecycles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lifecycles);
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
                if (cleanupContext.persistAWSResources()) {
                    lifecycle.persist();
                }
                lifecycle.close();
            } catch (IOException ie) {
                LOGGER.error("Failed to clean resources from {}", lifecycle, ie);
            }
        }
    }
}
