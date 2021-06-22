package com.aws.greengrass.testing.resources;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public interface AWSResourceLifecycle<C> extends Closeable {
    List<Class<? extends ResourceSpec<C, ? extends AWSResource<C>>>> getSupportedSpecs();

    <U extends ResourceSpec<C, R>, R extends AWSResource<C>> U create(U spec, AWSResources resources);

    <U extends ResourceSpec<C, R>, R extends AWSResource<C>> Stream<U> trackingSpecs(Class<U> specClass);

    void persist();

    void close() throws IOException;
}
