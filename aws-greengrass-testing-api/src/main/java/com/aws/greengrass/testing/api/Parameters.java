/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.Parameter;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Parameters {
    List<Parameter> available();

    /**
     * Loads all available parameters found on the classpath.
     *
     * @return
     */
    static Stream<Parameter> loadAll() {
        ServiceLoader<Parameters> parameters = ServiceLoader.load(Parameters.class);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(parameters.iterator(), Spliterator.NONNULL), false)
                .flatMap(params -> params.available().stream())
                .distinct();
    }
}
