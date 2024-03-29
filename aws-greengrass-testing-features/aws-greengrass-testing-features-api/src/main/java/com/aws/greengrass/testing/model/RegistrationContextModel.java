/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

@TestingModel
@Value.Immutable
interface RegistrationContextModel {
    @Value.Default
    default int connectionPort() {
        return 8443;
    }

    String rootCA();
}
