/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;

import java.util.Optional;

public interface ComponentPreparationService {
    Optional<ComponentOverrideNameVersion> prepare(ComponentOverrideNameVersion version);
}
