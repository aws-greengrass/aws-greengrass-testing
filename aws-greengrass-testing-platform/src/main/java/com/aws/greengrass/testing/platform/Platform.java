package com.aws.greengrass.testing.platform;

public interface Platform {
    Commands commands();

    PlatformFiles files();
}
