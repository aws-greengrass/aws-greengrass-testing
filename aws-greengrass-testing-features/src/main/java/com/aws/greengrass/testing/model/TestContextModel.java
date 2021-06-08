package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@TestingModel
@Value.Immutable
interface TestContextModel extends Closeable, DirectoryCleanupMixin {
    TestId testId();
    Path testDirectory();

    @Value.Default
    default Path installRoot() {
        String installRoot = System.getProperty("ggc.install.root");
        if (Objects.isNull(installRoot)) {
            return testDirectory().toAbsolutePath();
        } else {
            return Paths.get(installRoot, testDirectory().getFileName().toString());
        }
    }

    @Value.Default
    default String currentUser() {
        return System.getProperty("user.name");
    }

    @Override
    default void close() throws IOException {
        recursivelyDelete(testDirectory());
    }
}
