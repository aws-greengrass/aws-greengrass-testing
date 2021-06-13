package com.aws.greengrass.testing.resources;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public interface AWSResourceLifecycle<Client> extends Closeable {
    List<Class<? extends ResourceSpec<Client, ? extends AWSResource<Client>>>> getSupportedSpecs();

    <U extends ResourceSpec<Client, R>, R extends AWSResource<Client>> U create(U spec, AWSResources resources);

    <U extends ResourceSpec<Client, R>, R extends AWSResource<Client>> Stream<U> trackingSpecs(Class<U> specClass);

    void persist();

    void close() throws IOException;
}
