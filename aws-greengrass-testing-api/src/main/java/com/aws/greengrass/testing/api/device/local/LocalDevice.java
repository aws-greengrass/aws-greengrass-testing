/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.local;


import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.aws.greengrass.testing.api.model.TimeoutMultiplier;
import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@AutoService(Device.class)
public class LocalDevice implements Device {
    private static final Logger LOGGER = LogManager.getLogger(LocalDevice.class);
    public static final String TYPE = "LOCAL";
    private static final int BUFFER = 100_000;
    private final TimeoutMultiplier multiplier;
    private final String id = UUID.randomUUID().toString();

    public LocalDevice(final TimeoutMultiplier multiplier) {
        this.multiplier = multiplier;
    }

    public LocalDevice() {
        this(TimeoutMultiplier.builder().build());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
//        final ProcessBuilder builder = new ProcessBuilder().command(input.line());
        final ProcessBuilder builder;
        if (("cmd.exe /c").equals(input.line())) {
            builder = new ProcessBuilder().command("cmd.exe");
            builder.command().add("/c");
        } else {
            builder = new ProcessBuilder().command(input.line());
        }
        Optional.ofNullable(input.args()).ifPresent(args -> {
            args.forEach(builder.command()::add);
        });
        Optional.ofNullable(input.workingDirectory()).map(Path::toFile).ifPresent(builder::directory);
        try {
            LOGGER.debug("Running process: {}", builder.command());
            final Process process = builder.start();
            if (Objects.isNull(input.timeout())) {
                process.waitFor();
            } else {
                process.waitFor(multiplier.multiply(input.timeout()), TimeUnit.SECONDS);
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = flushStream(process.getErrorStream());
                throw new CommandExecutionException(error, exitCode, input);
            }
            return pump(process.getInputStream());
        } catch (IOException | InterruptedException ie) {
            throw new CommandExecutionException(ie, input);
        }
    }

    private byte[] pump(InputStream input) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER];
        int read;
        do {
            read = input.read(buffer);
            if (read >= 0) {
                baos.write(buffer, 0, read);
            }
        } while (read > 0);
        return baos.toByteArray();
    }

    private String flushStream(final InputStream input) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (Objects.nonNull(line)) {
                builder.append(line).append("\n");
                line = reader.readLine();
            }
        }
        return builder.toString();
    }

    @Override
    public PlatformOS platform() {
        return PlatformOS.currentPlatform();
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public void copyTo(String source, String destination) throws CopyException {
        final Path sourcePath = Paths.get(source);
        final Path destinationPath = Paths.get(destination);
        try {
            Files.copy(sourcePath, destinationPath);
            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> files = Files.walk(sourcePath)) {
                    files.forEach(file -> {
                        Path relativePath = sourcePath.relativize(file);
                        if (!relativePath.getFileName().toString().isEmpty()) {
                            try {
                                Files.copy(file, destinationPath.resolve(relativePath));
                            } catch (IOException e) {
                                throw new CopyException(e, file, destinationPath.resolve(relativePath));
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            throw new CopyException(e, sourcePath, destinationPath);
        }
    }
}
