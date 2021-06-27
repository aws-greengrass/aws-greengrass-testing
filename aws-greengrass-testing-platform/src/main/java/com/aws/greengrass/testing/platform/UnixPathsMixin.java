/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.model.PlatformOS;

interface UnixPathsMixin {
    PlatformOS host();

    default String formatToUnixPath(String incoming) {
        if (host().isWindows()) {
            return incoming
                    .replaceAll("^[A-Za-z]:", "")
                    .replace("\\", "/");
        }
        return incoming;
    }
}
