package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestingModel;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

@TestingModel
@Value.Immutable
interface GreengrassContextModel extends Closeable, DirectoryCleanupMixin {
    String version();

    Path archivePath();

    Path tempDirectory();

    default Path greengrassPath() {
        return tempDirectory().resolve("greengrass");
    }

    @Override
    default void close() throws IOException {
        recursivelyDelete(tempDirectory());
    }
}
