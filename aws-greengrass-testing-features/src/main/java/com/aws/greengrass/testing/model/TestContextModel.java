package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

@TestingModel
@Value.Immutable
interface TestContextModel extends Closeable, DirectoryCleanupMixin {
    TestId testId();
    Path testDirectory();

    @Value.Default
    default String currentUser() {
        return System.getProperty("user.name");
    }

    @Override
    default void close() throws IOException {
        // recursivelyDelete(testDirectory());
    }
}
