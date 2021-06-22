package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.api.model.CleanupContext;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Named;

@AutoService(Module.class)
public class GreengrassContextModule extends AbstractModule {
    private static final Logger LOGGER = LogManager.getLogger(GreengrassContextModule.class);
    private static final String NUCLEUS_VERSION = "ggc.version";
    private static final String NUCLEUS_ARCHIVE_PATH = "ggc.archive";
    private static String DEFAULT_NUCLEUS_VERSION;

    static void extractZip(ObjectMapper mapper, Path archivePath, Path stagingPath) throws IOException {
        LOGGER.info("Extracting {} into {}", archivePath, stagingPath);
        Files.createDirectory(stagingPath);
        try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(archivePath.toFile()))) {
            ZipEntry entry = zipStream.getNextEntry();
            while (Objects.nonNull(entry)) {
                final Path contentPath = stagingPath.resolve(entry.getName());
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
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            final CleanupContext cleanupContext) {
        try {
            final Path archivePath = Paths.get(Objects.requireNonNull(System.getProperty(NUCLEUS_ARCHIVE_PATH),
                    "Parameter " + NUCLEUS_ARCHIVE_PATH + " is required!"));
            final Path tempDirectory = Files.createTempDirectory("gg-testing-");
            extractZip(mapper, archivePath, tempDirectory.resolve("greengrass"));
            return GreengrassContext.builder()
                    .version(System.getProperty(NUCLEUS_VERSION, DEFAULT_NUCLEUS_VERSION))
                    .archivePath(archivePath)
                    .tempDirectory(tempDirectory)
                    .cleanupContext(cleanupContext)
                    .build();
        } catch (NullPointerException | IOException ie) {
            throw new ModuleProvisionException(ie);
        }
    }
}
