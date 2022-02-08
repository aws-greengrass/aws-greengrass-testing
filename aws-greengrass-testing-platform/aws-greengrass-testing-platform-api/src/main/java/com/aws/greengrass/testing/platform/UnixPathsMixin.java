/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

interface UnixPathsMixin {

    default String formatToUnixPath(String incoming) {
        return incoming
                .replaceAll("^[A-Za-z]:", "")
                .replace("\\", "/");
    }
}
