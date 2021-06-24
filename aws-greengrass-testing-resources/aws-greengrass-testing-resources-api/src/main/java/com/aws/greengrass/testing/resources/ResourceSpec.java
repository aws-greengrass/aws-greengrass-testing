/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources;

import org.immutables.value.Value;

import javax.annotation.Nullable;

public interface ResourceSpec<C, T extends AWSResource<C>> {
    ResourceSpec<C, T> create(C client, AWSResources resources);

    @Nullable
    T resource();

    @Value.Default
    default boolean created() {
        return false;
    }
}
