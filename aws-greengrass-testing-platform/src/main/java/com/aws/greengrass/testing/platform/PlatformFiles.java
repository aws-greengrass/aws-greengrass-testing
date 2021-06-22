package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface PlatformFiles {
    byte[] readBytes(Path filePath) throws CommandExecutionException;

    default String readString(Path filePath) throws CommandExecutionException {
        return new String(readBytes(filePath), StandardCharsets.UTF_8);
    }

    void delete(Path filePath) throws  CommandExecutionException;

    void makeDirectories(Path filePath) throws CommandExecutionException;

    List<Path> listContents(Path filePath) throws CommandExecutionException;

    /**
     * Perform a recursive copy from remote source to local destination.
     *
     * @param source Remote {@link Path} to copy from the device
     * @param destination Local {@link Path} to copy to the host
     * @throws CopyException Any propagated local nio utility IOException
     * @throws CommandExecutionException Any remote execution failing as a command exception
     */
    default void copyFrom(Path source, Path destination) throws CopyException, CommandExecutionException {
        final Path destinationRoot = destination.resolve(source.getFileName());
        try {
            Files.createDirectories(destinationRoot);
            for (Path file : listContents(source)) {
                final Path destinationFile = destination.resolve(file);
                Files.createDirectories(destinationFile.getParent());
                Files.write(destinationFile, readBytes(file));
            }
        } catch (IOException ie) {
            throw new CopyException(ie, source, destination);
        }
    }
}
