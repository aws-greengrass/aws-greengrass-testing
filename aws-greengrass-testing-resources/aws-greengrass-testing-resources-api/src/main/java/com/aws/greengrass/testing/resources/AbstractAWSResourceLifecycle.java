package com.aws.greengrass.testing.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class AbstractAWSResourceLifecycle<C> implements AWSResourceLifecycle<C> {
    private static final Logger LOGGER = LogManager.getLogger(AbstractAWSResourceLifecycle.class);
    protected C client;
    protected List<Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>> specClasses;
    protected List<ResourceSpec<C, ? extends AWSResource<C>>> specs;
    protected UUID uuid;

    @SafeVarargs
    public AbstractAWSResourceLifecycle(C client, Class<? extends ResourceSpec<C, ? extends AWSResource<C>>> ... specClass) {
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
        // Prepend so as to reverse the deletion
        specs.add(0, update);
        LOGGER.info("Created {} in {}",
                update.resource().getClass().getSimpleName(),
                getClass().getSimpleName().split("\\$", 2)[0]);
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
    public void close() {
        final ListIterator<ResourceSpec<C, ? extends AWSResource<C>>> iterator = specs.listIterator();
        while (iterator.hasNext()) {
            final ResourceSpec<C, ? extends AWSResource<C>> spec = iterator.next();
            try {
                spec.resource().remove(client);
                LOGGER.info("Removed {} in {}",
                        spec.resource().getClass().getSimpleName(),
                        getClass().getSimpleName().split("\\$", 2)[0]);
            } catch (Throwable ex) {
                // Don't prevent SDK failures from removing other resources being tracked.
                LOGGER.error("Failed to remove {} in {}", spec.resource(), getClass().getSimpleName(), ex);
            }
            iterator.remove();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAWSResourceLifecycle<?> that = (AbstractAWSResourceLifecycle<?>) o;
        return Objects.equals(client, that.client)
                && Objects.equals(specClasses, that.specClasses)
                && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, specClasses, uuid);
    }
}
