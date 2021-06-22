package com.aws.greengrass.testing.api.device.exception;

import java.nio.file.Path;

public class CopyException extends RuntimeException {
    private static final long serialVersionUID = -7890122559485268386L;

    private final Path source;
    private final Path destination;

    /**
     * Create a {@link CopyException} with a cause, the source, destination values.
     *
     * @param ex The underlying cause of the copy failure
     * @param source The source {@link Path}. Does not need to be absolute.
     * @param destination The destination {@link Path}. Usually absolute.
     */
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
