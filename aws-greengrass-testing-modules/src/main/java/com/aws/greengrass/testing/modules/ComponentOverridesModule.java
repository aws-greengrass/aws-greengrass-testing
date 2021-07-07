/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import java.util.Optional;
import javax.inject.Singleton;

@AutoService(Module.class)
public class ComponentOverridesModule extends AbstractModule {
    private static final int MAX_SPLIT_LIMIT = 2;

    @Provides
    @Singleton
    static ComponentOverrides providesComponentOverrides(ParameterValues parameterValues) {
        ComponentOverrides.Builder builder = ComponentOverrides.builder();
        parameterValues.getString(ModuleParameters.COMPONENT_BUCKET).ifPresent(builder::bucketName);
        parameterValues.getString(ModuleParameters.COMPONENT_OVERRIDES).ifPresent(overrideString -> {
            final String[] components = overrideString.split("\\s*,\\s*");
            for (String component : components) {
                final String[] nameVersionParts = component.split(":", MAX_SPLIT_LIMIT);
                final String[] versionParts = nameVersionParts[1].split(":", MAX_SPLIT_LIMIT);
                String type;
                String version;
                if (versionParts.length == MAX_SPLIT_LIMIT) {
                    type = versionParts[0];
                    version = versionParts[1];
                } else {
                    // Support shorthand for cloud
                    type = "cloud";
                    version = versionParts[0];
                }
                builder.putOverrides(nameVersionParts[0], ComponentOverrideVersion.of(type, version));
            }
        });
        return builder.build();
    }
}
