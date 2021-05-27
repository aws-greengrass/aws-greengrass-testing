package com.aws.greengrass.testing.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class AbstractAWSResourceLifecycle<C> implements AWSResourceLifecycle<C> {
    private static final Logger LOGGER = LogManager.getLogger(AbstractAWSResourceLifecycle.class);
    protected C client;
    protected List<Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>> specClasses;
    protected List<ResourceSpec<C, ? extends AWSResource<C>>> specs;
    protected List<AWSResource<C>> resources;

    @SafeVarargs
    public AbstractAWSResourceLifecycle(C client, Class<? extends ResourceSpec<C, ? extends AWSResource<C>>> ... specClass) {
        this.client = client;
        this.specClasses = Arrays.asList(specClass);
        this.specs = new ArrayList<>();
        this.resources = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends ResourceSpec<C, R>, R extends AWSResource<C>> U create(U spec, AWSResources resources) {
        ResourceSpec<C,R> update = spec.create(client, resources);
        specs.add(update);
        this.resources.add(update.resource());
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
        final Iterator<? extends AWSResource<C>> iterator = resources.listIterator();
        while (iterator.hasNext()) {
            final AWSResource<C> resource = iterator.next();
            try {
                resource.remove(client);
            } catch (Throwable ex) {
                // Don't prevent SDK failures from removing other resources being tracked.
                LOGGER.error("Failed to remove {} in {}", resource, getClass().getSimpleName(), ex);
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
                && Objects.equals(specs, that.specs)
                && Objects.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, specClasses, specs, resources);
    }
}
