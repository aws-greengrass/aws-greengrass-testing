/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

interface PersistenceBuilder<T extends PersistenceBuilder<T>> {
    T persistAWSResources(boolean awsResources);

    T persistInstalledSoftware(boolean installedSoftware);

    T persistGeneratedFiles(boolean generatedFiles);
}
