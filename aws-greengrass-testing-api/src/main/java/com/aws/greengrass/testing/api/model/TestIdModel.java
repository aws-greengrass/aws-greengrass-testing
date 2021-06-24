/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.immutables.value.Value;

import java.util.StringJoiner;

@TestingModel
@Value.Immutable
interface TestIdModel {
    @Value.Default
    default String prefix() {
        return "";
    }

    String id();

    default String prefixedId() {
        StringJoiner joiner = new StringJoiner("-");
        if (!prefix().isEmpty()) {
            joiner.add(prefix());
        }
        return joiner.add(id()).toString();
    }

    default String idFor(String type) {
        return String.format("%s-%s", prefixedId(), type);
    }
}
