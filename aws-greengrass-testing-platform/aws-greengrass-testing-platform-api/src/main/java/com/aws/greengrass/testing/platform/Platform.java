/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

public interface Platform {
    Commands commands();

    PlatformFiles files();

    default NetworkUtils getNetworkUtils() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}

