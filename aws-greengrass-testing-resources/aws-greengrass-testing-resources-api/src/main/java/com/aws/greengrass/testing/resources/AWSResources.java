/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.TestId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AWSResources implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger(AWSResources.class);
    private final Set<AWSResourceLifecycle> lifecycles;
    private final CleanupContext cleanupContext;
    private final TestId testId;
    private final Set<AWSResourceLifecycle> usedcycles;

    /**
     * Create a {@link AWSResources} instance with a custom {@link CleanupContext} and {@link TestId}.
     *
     * @param lifecycles Distinct collection of {@link AWSResourceLifecycle}
     * @param cleanupContext Custom {@link CleanupContext}
     * @param testId Custom {@link TestId}
     */
    public AWSResources(
            Set<AWSResourceLifecycle> lifecycles,
            CleanupContext cleanupContext,
            TestId testId) {
        this.lifecycles = lifecycles;
        this.cleanupContext = cleanupContext;
        this.testId = testId;
        this.usedcycles = new LinkedHashSet<>();
    }

    /**
     * Create a {@link AWSResources} that cleans all resources and uses a generic {@link UUID} based tag.
     *
     * @param lifecycles Distinct collection of {@link AWSResourceLifecycle}'s
     */
    public AWSResources(Set<AWSResourceLifecycle> lifecycles) {
        this(lifecycles,
                CleanupContext.builder().build(),
                TestId.builder()
                        .id(UUID.randomUUID().toString())
                        .build());
    }

    /**
     * Attempt to find a concrete lifecycle implementation by type.
     *
     * @param lifecycleType A {@link Class} that represents the {@link AWSResourceLifecycle}
     * @param <U> AWSResourceLifecycle implementation type
     * @return
     */
    public <U extends AWSResourceLifecycle> U lifecycle(Class<U> lifecycleType) {
        return lifecycles.stream()
                .peek(lc -> LOGGER.debug("Available lifecycle {}", lc))
                .filter(lc -> lifecycleType.isAssignableFrom(lc.getClass()))
                .map(lifecycleType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find " + lifecycleType));
    }

    /**
     * Get a {@link Map} that contains all of the default resource tags to be used in resource tagging.
     *
     * @return
     */
    public Map<String, String> generateResourceTags() {
        return Collections.unmodifiableMap(new HashMap<String, String>() {
            {
                put("Testing", "Greengrass");
                put("TestId", testId.prefixedId());
            }
        });
    }

    /**
     * All {@link ResourceSpec} create calls are handled through create entry point.
     *
     * @param spec A {@link ResourceSpec} implementation that results in an AWS resource.
     * @param <C> A generic to represent AWS client
     * @param <U> {@link ResourceSpec} implementation type
     * @param <R> {@link AWSResource} implementation type
     * @return
     */
    @SuppressWarnings("unchecked")
    public <C, U extends ResourceSpec<C, R>, R extends AWSResource<C>> U create(U spec) {
        return find((Class<U>) spec.getClass())
                .map(lc -> lc.create(spec, this))
                .orElseThrow(() -> new IllegalArgumentException("Could not find lifecycle for " + spec.getClass()));
    }

    /**
     * Get all tracked {@link ResourceSpec} specs that the concrete specClass.
     *
     * @param specClass All {@link ResourceSpec} that match specClass
     * @param <C> A generic to represent AWS client
     * @param <U> {@link ResourceSpec} implementation type
     * @param <R> {@link AWSResource} implementation type
     * @return
     */
    public <C, U extends ResourceSpec<C, R>, R extends AWSResource<C>> Stream<U> trackingSpecs(Class<U> specClass) {
        return find(specClass)
                .map(lc -> lc.trackingSpecs(specClass))
                .orElseGet(Stream::empty);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AWSResources that = (AWSResources) o;
        return Objects.equals(lifecycles, that.lifecycles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lifecycles);
    }

    @SuppressWarnings("unchecked")
    private <C, S extends ResourceSpec<C, R>, R extends AWSResource<C>> Optional<AWSResourceLifecycle<C>> find(
            Class<S> specClass) {
        return lifecycles.stream()
                .filter(lc -> lc.getSupportedSpecs().contains(specClass))
                .peek(usedcycles::add)
                .findFirst()
                .map(lc -> (AWSResourceLifecycle<C>) lc);
    }

    @Override
    public void close() {
        Set<AWSResourceLifecycle> remaining = new HashSet<>(lifecycles);
        List<AWSResourceLifecycle> insertionOrder = new ArrayList<>(usedcycles);
        Collections.reverse(insertionOrder);
        Consumer<AWSResourceLifecycle> closeLifecycle = this::closeSingle;
        insertionOrder.forEach(closeLifecycle.andThen(remaining::remove));
        remaining.forEach(closeLifecycle);
    }

    private void closeSingle(AWSResourceLifecycle lifecycle) {
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
