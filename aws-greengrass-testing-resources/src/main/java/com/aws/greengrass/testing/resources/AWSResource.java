package com.aws.greengrass.testing.resources;

public interface AWSResource<C> {
    void remove(C client);
}
