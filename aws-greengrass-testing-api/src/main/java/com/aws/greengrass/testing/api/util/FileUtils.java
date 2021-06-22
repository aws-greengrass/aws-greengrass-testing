package com.aws.greengrass.testing.api.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class FileUtils {
    private FileUtils() {
    }

    /**
     * Recursively delete a file directory by path.
     * <strong>Note</strong>: This strictly pertains to deleting directories on the host agent.
     *
     * @param directory Directory to delete completely
     * @throws IOException Propagated IOException from surrounding nio utility methods
     */
    public static void recursivelyDelete(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }
}
