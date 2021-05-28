package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.nio.file.Path;

public class DefaultGreengrass implements Greengrass {
    private final Path rootPath;
    private final String envStage;
    private final String region;
    // TODO: Replace with Platform
    private final Device device;

    public DefaultGreengrass(
            final Device device,
            String envStage,
            String region,
            Path rootPath) {
        this.device = device;
        this.rootPath = rootPath;
        this.envStage = envStage;
        this.region = region;
    }

    @Override
    public void install() {
        device.execute(CommandInput.builder()
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
        // TODO: launch from launcher
    }

    @Override
    public void stop() {
        // TODO: find pids and SIGTERM
    }
}
