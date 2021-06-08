package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import javax.inject.Singleton;
import java.util.Optional;

@AutoService(Module.class)
public class ComponentOverridesModule extends AbstractModule {
    private static final int MAX_SPLIT_LIMIT = 2;
    private static final String COMPONENT_BUCKET = "gg.component.bucket";
    private static final String COMPONENT_OVERRIDES = "gg.component.overrides";

    @Provides
    @Singleton
    static ComponentOverrides providesComponentOverrides() {
        ComponentOverrides.Builder builder = ComponentOverrides.builder()
                .bucketName(System.getProperty(COMPONENT_BUCKET));
        Optional.ofNullable(System.getProperty(COMPONENT_OVERRIDES)).ifPresent(overrideString -> {
            // -Dgg.component.overrides=aws.greengrass.Nucleus:cloud:LATEST,aws.greengrass.LocalDebugConsole:file:/path/to/recipe.yml
            final String[] components = overrideString.split("\\s*,\\s*");
            for (String component : components) {
                final String[] nameVersionParts = component.split(":", MAX_SPLIT_LIMIT);
                final String[] versionParts = nameVersionParts[1].split(":", MAX_SPLIT_LIMIT);
                String type, version;
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
