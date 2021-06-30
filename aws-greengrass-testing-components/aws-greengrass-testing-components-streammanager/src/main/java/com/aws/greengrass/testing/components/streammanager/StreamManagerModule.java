/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.components.streammanager;

import com.amazonaws.greengrass.streammanager.client.StreamManagerClient;
import com.amazonaws.greengrass.streammanager.client.StreamManagerClientFactory;
import com.amazonaws.greengrass.streammanager.client.exception.StreamManagerException;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class StreamManagerModule {

    @Provides
    @Singleton
    static StreamManagerClient providesStreamManagerClient() {
        try {
            return StreamManagerClientFactory.defaultClient();
        } catch (StreamManagerException e) {
            throw new RuntimeException(e);
        }
    }
}
