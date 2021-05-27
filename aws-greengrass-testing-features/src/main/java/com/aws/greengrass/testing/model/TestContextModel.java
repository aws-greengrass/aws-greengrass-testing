package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@TestingModel
@Value.Immutable
interface TestContextModel extends Closeable {
    TestId testId();
    Path testDirectory();

    @Value.Default
    default String currentUser() {
        return System.getProperty("user.name");
    }

    @Override
    default void close() throws IOException {
        try (Stream<Path> files = Files.walk(testDirectory())) {
            files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
