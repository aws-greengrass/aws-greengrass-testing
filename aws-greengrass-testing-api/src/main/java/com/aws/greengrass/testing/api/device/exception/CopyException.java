package com.aws.greengrass.testing.api.device.exception;

import java.nio.file.Path;

public class CopyException extends RuntimeException {
    private static final long serialVersionUID = -7890122559485268386L;

    private final Path source;
    private final Path destination;

    public CopyException(Throwable ex, Path source, Path destination) {
        super(ex);
        this.destination = destination;
        this.source = source;
    }

    public Path source() {
        return source;
    }

    public Path destination() {
        return destination;
    }
}
