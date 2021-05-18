package com.aws.greengrass.testing.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public abstract class AbstractAWSResourceLifecycle<C> implements AWSResourceLifecycle<C> {
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
            resource.remove(client);
            iterator.remove();
        }
    }
}
