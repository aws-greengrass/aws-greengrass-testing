package com.aws.greengrass.testing;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;

public class DefaultGreengrass implements Greengrass {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGreengrass.class);
    private final Path rootPath;
    private final String envStage;
    private final String region;
    private final Platform platform;
    private int greengrassProcess;

    public DefaultGreengrass(
            final Platform platform,
            String envStage,
            String region,
            Path rootPath) {
        this.platform = platform;
        this.rootPath = rootPath;
        this.envStage = envStage;
        this.region = region;
    }

    @Override
    public void install() {
        platform.commands().execute(CommandInput.builder()
                .line("java").addArgs(
                        "-Droot=" + rootPath.toString(),
                        "-Dlog.store=FILE",
                        "-jar", rootPath.resolve("greengrass/lib/Greengrass.jar").toString(),
                        "--aws-region", region,
                        "--env-stage", envStage,
                        "--start", "false")
                .build());
    }

    @Override
    public void start() {
        Path loaderScriptPath = rootPath.resolve("alts/current/distro/bin/loader");
        greengrassProcess = platform.commands().executeInBackground(CommandInput.builder()
                .line(loaderScriptPath.toString())
                .build());
        LOGGER.info("Starting greengrass on {}", greengrassProcess);
    }

    @Override
    public void stop() {
        try {
            platform.commands().kill(Arrays.asList(greengrassProcess));
        } catch (CommandExecutionException e) {
            LOGGER.warn("Failed to kill process {}: {}", greengrassProcess, e.getMessage());
        }
    }
}
