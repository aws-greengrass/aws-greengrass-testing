/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.exception;

public class PlatformResolutionException extends RuntimeException {
    private static final long serialVersionUID = -8978703299393804392L;

    public PlatformResolutionException(String message) {
        super(message);
    }
}
