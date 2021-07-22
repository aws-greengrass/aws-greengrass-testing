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
import java.time.Duration;

class RotatingProfileAwsCredentialsProvider implements AwsCredentialsProvider {
    private final String profileFile;
    private final Duration duration;
    private long lastRotation;
    private AwsCredentialsProvider profileProvider;

    public RotatingProfileAwsCredentialsProvider(final String profileFile, final Duration duration) {
        this.profileFile = profileFile;
        this.duration = duration;
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
        if (System.currentTimeMillis() - lastRotation > duration.toMillis()) {
            rotateProvider();
        }
        return profileProvider.resolveCredentials();
    }
}
