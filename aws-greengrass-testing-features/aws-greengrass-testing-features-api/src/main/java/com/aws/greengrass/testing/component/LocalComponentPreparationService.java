/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.platform.Platform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;

public class LocalComponentPreparationService implements ComponentPreparationService {

    private static final Logger LOGGER = LogManager.getLogger(LocalComponentPreparationService.class);

    private static final String COMPONENT_VERSION = "ComponentVersion";
    private static final String MANIFESTS = "Manifests";
    private static final String ARTIFACTS = "Artifacts";
    private static final String URI = "URI";
    public static final String LOCAL_STORE = "testlocalstore";
    public static final String ARTIFACTS_DIR = "artifacts";
    public static final String RECIPE_DIR = "recipes";

    private final Platform platform;
    private final TestContext testContext;
    private final ContentLoader loader;
    private final ObjectMapper mapper;

    @FunctionalInterface
    public interface ContentLoader {
        InputStream load(String version) throws IOException;
    }

    /**
     * Constructor.
     * @param platform platform for the device
     * @param testContext {@link TestContext}
     * @param objectMapper {@link ObjectMapper}
     */
    @Inject
    public LocalComponentPreparationService(Platform platform,
                                            TestContext testContext,
                                            @Named(JacksonModule.YAML) ObjectMapper objectMapper) {
        this.platform = platform;
        this.testContext = testContext;
        this.loader = value -> Files.newInputStream(Paths.get(value));
        this.mapper = objectMapper;
    }

    @Override
    public Optional<ComponentOverrideNameVersion> prepare(ComponentOverrideNameVersion overrideNameVersion) {
        try {
            Map<String, Object> recipe = mapper.readValue(loader.load(overrideNameVersion.version().value()),
                    new TypeReference<Map<String, Object>>() {
            });
            String componentName = overrideNameVersion.name();
            String componentVersion = recipe.get(COMPONENT_VERSION).toString();
            List<Map<String, Object>> manifests = (List<Map<String, Object>>) recipe.get(MANIFESTS);

            for (Map<String, Object> manifest : manifests) {
                List<Map<String, Object>> artifacts = (List<Map<String, Object>>) manifest.get(ARTIFACTS);
                Iterator<Map<String, Object>> iterator = artifacts.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> artifact = iterator.next();
                    String uri = artifact.get(URI).toString();
                    if (uri.startsWith("file")) {
                        iterator.remove();
                        String filepath = uri.split(":")[1];
                        copyArtifactToLocalStore(Paths.get(filepath),componentName,componentVersion);
                    }
                }
                if (artifacts.isEmpty()) {
                    manifest.remove(ARTIFACTS);
                }
            }

            copyRecipeToLocalStore(mapper.writeValueAsString(recipe), componentName,
                    componentVersion);

            return Optional.of(ComponentOverrideNameVersion.builder()
                    .from(overrideNameVersion)
                    .version(ComponentOverrideVersion.builder()
                            .from(overrideNameVersion.version())
                            .value(componentVersion)
                            .build())
                    .build());
        } catch (IOException ie) {
            LOGGER.error("Failed to load {}:", overrideNameVersion, ie);
            return Optional.empty();
        }
    }

    private void copyArtifactToLocalStore(Path artifactFilePath, String componentName, String componentVersion) {
        Path localStoreArtifactPath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(ARTIFACTS_DIR);
        Path componentArtifactPath = localStoreArtifactPath.resolve(componentName).resolve(componentVersion);

        platform.files().makeDirectories(componentArtifactPath);
        platform.files().copyTo(artifactFilePath, componentArtifactPath.resolve(artifactFilePath.getFileName()));
    }

    private void copyRecipeToLocalStore(String recipe, String componentName, String componentVersion) {
        Path localStoreRecipePath = testContext.testDirectory().resolve(LOCAL_STORE).resolve(RECIPE_DIR);
        platform.files().makeDirectories(localStoreRecipePath);
        // TODO: Add conditional for json as well
        Path componentRecipePath = localStoreRecipePath.resolve(componentName + "-" + componentVersion + ".yaml");
        platform.files().writeBytes(componentRecipePath, recipe.getBytes(StandardCharsets.UTF_8));
    }
}