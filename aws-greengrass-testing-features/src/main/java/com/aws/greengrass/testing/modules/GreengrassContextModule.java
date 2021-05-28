package com.aws.greengrass.testing.modules;

import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@AutoService(Module.class)
public class GreengrassContextModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(GreengrassContextModule.class);
    private static final int MAX_BUFFER = 1_000_000;
    private static final String NUCLEUS_VERSION = "ggc.version";
    private static final String NUCLEUS_ARCHIVE_PATH = "ggc.archive";

    static void extractZip(Path archivePath, Path stagingPath) throws IOException {
        LOGGER.info("Extracting {} into {}", archivePath, stagingPath);
        Files.createDirectory(stagingPath);
        try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(archivePath.toFile()))) {
            ZipEntry entry = zipStream.getNextEntry();
            while (Objects.nonNull(entry)) {
                final Path contentPath = stagingPath.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(contentPath);
                    continue;
                } else if (!Files.exists(contentPath.getParent())) {
                    Files.createDirectories(contentPath.getParent());
                }
                LOGGER.info("Extracting {} into {}", entry.getName(), contentPath);
                try (FileOutputStream output = new FileOutputStream(contentPath.toFile())) {
                    final byte[] buffer = new byte[MAX_BUFFER];
                    int read = 0;
                    do {
                        read = zipStream.read(buffer);
                        if (read > 0) {
                            output.write(buffer, 0, read);
                        }
                    } while (read > 0);
                }
                entry = zipStream.getNextEntry();
            }
            zipStream.closeEntry();
        }
    }

    @Provides
    @Singleton
    static GreengrassContext providesNucleusContext() {
        final Path archivePath = Paths.get(System.getProperty(NUCLEUS_ARCHIVE_PATH));
        try {
            final Path tempDirectory = Files.createTempDirectory("gg-testing-");
            extractZip(archivePath, tempDirectory.resolve("greengrass"));
            return GreengrassContext.builder()
                    .version(System.getProperty(NUCLEUS_VERSION))
                    .archivePath(archivePath)
                    .tempDirectory(tempDirectory)
                    .build();
        } catch (IOException ie) {
            throw new ModuleProvisionException(ie);
        }
    }
}
