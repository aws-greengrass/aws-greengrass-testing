/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public abstract class AbstractAWSResourceLifecycle<C> implements AWSResourceLifecycle<C> {
    private static final Logger LOGGER = LogManager.getLogger(AbstractAWSResourceLifecycle.class);
    private static final long MAX_WAIT_TIME_FOR_CLOUD_OPERATION = 5;
    protected C client;
    protected List<Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>> specClasses;
    protected List<ResourceSpec<C, ? extends AWSResource<C>>> specs;
    protected UUID uuid;

    /**
     * Create a {@link AWSResourceLifecycle} using a collection of tracking classes and underlying client.
     *
     * @param client A type of AWS client to handle the tracking {@link ResourceSpec}
     * @param specClass a {@link ResourceSpec} implementation class
     */
    @SafeVarargs
    public AbstractAWSResourceLifecycle(C client,
                                        Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>...specClass) {
        this.client = client;
        this.specClasses = Arrays.asList(specClass);
        this.specs = new ArrayList<>();
        this.uuid = UUID.randomUUID();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends ResourceSpec<C, R>, R extends AWSResource<C>> U create(U spec, AWSResources resources) {
        if (spec.created()) {
            return spec;
        }
        ResourceSpec<C,R> update = spec.create(client, resources);
        // check if the resource is available in cloud
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future future = executor.submit(() -> {
            while (!update.availableInCloud(client)) {
                try {
                    Thread.sleep(Duration.ofSeconds(1).toMillis());
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while waiting for resource to get created in cloud");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        try {
            future.get(MAX_WAIT_TIME_FOR_CLOUD_OPERATION, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while checking if the resource is created in cloud. Resource type: {}",
                    spec.getClass().getSimpleName());
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("Check for resources created in cloud failed. Resource type: {}. Moving on",
                    spec.getClass().getSimpleName());
        }

        // Prepend so as to reverse the deletion
        specs.add(0, update);
        LOGGER.info("Created {} in {}", update.resource().getClass().getSimpleName(), displayName());
        return (U) update;
    }

    @Override
    public List<Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>> getSupportedSpecs() {
        return specClasses;
    }

    @Override
    public <U extends ResourceSpec<C, R>, R extends AWSResource<C>> Stream<U> trackingSpecs(Class<U> specClass) {
        return specs.stream().filter(spec -> spec.getClass() == specClass).map(specClass::cast);
    }

    @Override
    public void persist() {
        // TODO: dump to a place where it can be deserialized for later.
        specs.clear();
        LOGGER.info("Persisting resources tracked in {}", displayName());
    }

    @Override
    public void close() {
        final ListIterator<ResourceSpec<C, ? extends AWSResource<C>>> iterator = specs.listIterator();
        while (iterator.hasNext()) {
            final ResourceSpec<C, ? extends AWSResource<C>> spec = iterator.next();
            try {
                spec.resource().remove(client);
                LOGGER.info("Removed {} in {}", spec.resource().getClass().getSimpleName(), displayName());
            } catch (Throwable ex) {
                // Don't prevent SDK failures from removing other resources being tracked.
                LOGGER.error("Failed to remove {} in {}", spec.resource(), displayName(), ex);
            }
            iterator.remove();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractAWSResourceLifecycle<?> that = (AbstractAWSResourceLifecycle<?>) o;
        return Objects.equals(client, that.client)
                && Objects.equals(specClasses, that.specClasses)
                && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, specClasses, uuid);
    }

    private String displayName() {
        return getClass().getSimpleName().split("\\$", 2)[0];
    }
}
