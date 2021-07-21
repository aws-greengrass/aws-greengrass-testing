/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

class RotatingProfileAwsCredentialsProvider implements AwsCredentialsProvider {
    private static final long ROTATION_TIMEOUT = TimeUnit.MINUTES.toMillis(15);
    private final String profileFile;
    private long lastRotation;
    private AwsCredentialsProvider profileProvider;

    public RotatingProfileAwsCredentialsProvider(final String profileFile) {
        this.profileFile = profileFile;
        rotateProvider();
    }

    private void rotateProvider() {
        this.profileProvider = ProfileCredentialsProvider.builder()
                .profileFile(ProfileFile.builder()
                        .type(ProfileFile.Type.CREDENTIALS)
                        .content(Paths.get(profileFile))
                        .build())
                .build();
        this.lastRotation = System.currentTimeMillis();
    }

    @Override
    public synchronized AwsCredentials resolveCredentials() {
        if (System.currentTimeMillis() - lastRotation > ROTATION_TIMEOUT) {
            rotateProvider();
        }
        return profileProvider.resolveCredentials();
    }
}
