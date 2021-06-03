package com.aws.greengrass.testing.api.device.local;


import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.device.model.PlatformOS;
import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@AutoService(Device.class)
public class LocalDevice implements Device {
    private static final Logger LOGGER = LogManager.getLogger(LocalDevice.class);
    private static final String TYPE = "LOCAL";

    @Override
    public String id() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        final ProcessBuilder builder = new ProcessBuilder().command(input.line());
        Optional.ofNullable(input.args()).ifPresent(args -> {
            args.forEach(builder.command()::add);
        });
        try {
            LOGGER.info("Runninng process: {}", builder.command());
            final Process process = builder.start();
            if (Objects.isNull(input.timeout())) {
                process.waitFor();
            } else {
                process.waitFor(input.timeout(), TimeUnit.SECONDS);
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = flushStream(process.getErrorStream());
                throw new CommandExecutionException(error, exitCode, input);
            }
            return flushStream(process.getInputStream()).getBytes(StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException ie) {
            throw new CommandExecutionException(ie, input);
        }
    }

    private String flushStream(final InputStream input) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (Objects.nonNull(line)) {
                builder.append(line);
                line = reader.readLine();
            }
        }
        return builder.toString();
    }

    @Override
    public boolean exists(Path file) {
        return Files.exists(file);
    }

    @Override
    public PlatformOS platform() {
        return PlatformOS.builder()
                .name(System.getProperty("os.name"))
                .arch(System.getProperty("os.arch"))
                .build();
    }

    @Override
    public void copy(Path source, Path destination) {
        try {
            Files.copy(source, destination);
            if (Files.isDirectory(source)) {
                try (Stream<Path> files = Files.walk(source)) {
                    files.forEach(file -> {
                        Path relativePath = source.relativize(file);
                        if (!relativePath.getFileName().toString().isEmpty()) {
                            try {
                                Files.copy(file, destination.resolve(relativePath));
                            } catch (IOException e) {
                                throw new CopyException(e, file, destination.resolve(relativePath));
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            throw new CopyException(e, source, destination);
        }
    }
}
