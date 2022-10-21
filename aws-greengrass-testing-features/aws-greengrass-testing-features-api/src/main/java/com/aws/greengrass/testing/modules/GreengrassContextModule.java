/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.api.model.InitializationContext;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.utils.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Named;

@AutoService(Module.class)
public class GreengrassContextModule extends AbstractModule {
    private static final Logger LOGGER = LogManager.getLogger(GreengrassContextModule.class);
    static String DEFAULT_NUCLEUS_VERSION;
    private static final String GREENGRASS_RECIPE_FILE_LOCATION = "conf/recipe.yaml";
    private static final String COMPONENT_VERSION_KEY = "ComponentVersion";
    private static final String TARGET_DIRECTORY = "greengrass";

    static void extractZip(ObjectMapper mapper, Path archivePath, Path stagingPath) throws IOException {
        LOGGER.info("Extracting {} into {}", archivePath, stagingPath);
        Files.createDirectory(stagingPath);
        try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(archivePath.toFile()))) {
            ZipEntry entry = zipStream.getNextEntry();
            while (Objects.nonNull(entry)) {
                final Path contentPath = stagingPath.resolve(entry.getName());
                if (!contentPath.toFile().getAbsolutePath().startsWith(stagingPath.toAbsolutePath().toString())) {
                    LOGGER.warn("Archive attempted to write {} outside of {}, skipping",
                            contentPath.toFile().getAbsolutePath(), stagingPath.toAbsolutePath());
                    entry = zipStream.getNextEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(contentPath);
                    entry = zipStream.getNextEntry();
                    continue;
                } else if (!Files.exists(contentPath.getParent())) {
                    Files.createDirectories(contentPath.getParent());
                }
                LOGGER.debug("Extracting {} into {}", entry.getName(), contentPath);
                try (FileOutputStream output = new FileOutputStream(contentPath.toFile())) {
                    IoUtils.copy(zipStream, output);
                } catch (FileNotFoundException e) {
                    throw new FileNotFoundException("file doesn't exist");
                }
                if (entry.getName().contains("recipe.yaml")) {
                    JsonNode node = mapper.readTree(contentPath.toFile());
                    DEFAULT_NUCLEUS_VERSION = node.get("ComponentVersion").asText();
                }
                entry = zipStream.getNextEntry();
            }
            zipStream.closeEntry();
        }
    }

    @Provides
    @Singleton
    static GreengrassContext providesNucleusContext(
            final ParameterValues parameterValues,
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            final InitializationContext initializationContext,
            final CleanupContext cleanupContext) throws IOException {
        FileInputStream nucleusRecipeInStream = null;
        try {
            Path tempDirectory;
            Optional<String> tempDirectoryName = parameterValues.getString(FeatureParameters.TEST_TEMP_PATH);
            Optional<String> nucleusVersion = parameterValues.getString(FeatureParameters.NUCLEUS_VERSION);
            if (tempDirectoryName.isPresent()) {
                tempDirectory = Paths.get(tempDirectoryName.get());
                Files.createDirectories(tempDirectory);
            } else {
                tempDirectory = Files.createTempDirectory("gg-testing-");
            }
            if (!initializationContext.persistInstalledSoftware()) {
                final Path archivePath = parameterValues.getString(FeatureParameters.NUCLEUS_ARCHIVE_PATH)
                        .map(Paths::get)
                        .orElseThrow(() -> new IllegalArgumentException("Parameter "
                                + FeatureParameters.NUCLEUS_ARCHIVE_PATH + " is required if not testing against "
                                + "pre-installed versions of Greengrass on the device."));
                extractZip(mapper, archivePath, tempDirectory.resolve(TARGET_DIRECTORY));

                if (!nucleusVersion.isPresent()) {
                    File nucleusRecipeFile = tempDirectory.resolve(TARGET_DIRECTORY)
                            .resolve(GREENGRASS_RECIPE_FILE_LOCATION).toFile();
                    nucleusRecipeInStream = new FileInputStream(nucleusRecipeFile);
                    Map<String, Object> recipeObject =
                            mapper.readValue(nucleusRecipeInStream, new TypeReference<Map<String, Object>>() {});
                    nucleusVersion = Optional.ofNullable(String.valueOf(recipeObject.get(COMPONENT_VERSION_KEY)));
                }
            } else {
                if (!parameterValues.getString(FeatureParameters.NUCLEUS_ARCHIVE_PATH).get().isEmpty()) {
                    LOGGER.warn("Testing with PreInstalled greengrass, the path to greengrass nucleus "
                            + "zip will be ignored");
                }
            }

            return GreengrassContext.builder()
                    .version(nucleusVersion.orElse(DEFAULT_NUCLEUS_VERSION))
                    .tempDirectory(tempDirectory)
                    .cleanupContext(cleanupContext)
                    .build();
        } catch (NullPointerException | IOException ie) {
            LOGGER.error("Failed to provision Greengrass testing context", ie);
            throw new ModuleProvisionException(ie);
        } finally {
            if (nucleusRecipeInStream != null) {
                nucleusRecipeInStream.close();
            }
        }
    }
}
