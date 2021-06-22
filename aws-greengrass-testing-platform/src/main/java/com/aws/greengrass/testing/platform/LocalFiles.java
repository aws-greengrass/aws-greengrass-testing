package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalFiles implements PlatformFiles {
    @Override
    public byte[] readBytes(Path filePath) throws CommandExecutionException {
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("read: " + filePath));
        }
    }

    @Override
    public void delete(Path filePath) throws CommandExecutionException {
        try {
            FileUtils.recursivelyDelete(filePath);
        } catch (IOException ie) {
            throw new CommandExecutionException(ie, CommandInput.of("delete: " + filePath));
        }
    }

    @Override
    public void makeDirectories(Path filePath) throws CommandExecutionException {
        try {
            Files.createDirectories(filePath);
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("makeDirectories: " + filePath));
        }
    }

    @Override
    public List<Path> listContents(Path filePath) throws CommandExecutionException {
        if (Files.isRegularFile(filePath)) {
            return Arrays.asList(filePath);
        }
        try (Stream<Path> files = Files.walk(filePath)) {
            return files.sorted(Comparator.reverseOrder())
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new CommandExecutionException(e, CommandInput.of("listContents: " + filePath));
        }
    }
}
