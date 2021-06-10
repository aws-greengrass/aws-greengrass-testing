package com.aws.greengrass.testing.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class IOUtils {
    private static final int BUFFER = 1_000_000;

    private IOUtils() {
    }

    public static void pumpStreams(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int read;
        do {
            read = inputStream.read(buffer);
            if (read >= 0) {
                outputStream.write(buffer, 0, read);
            }
        } while (read > 0);
    }

    public static String toString(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line = reader.readLine();
        while (Objects.nonNull(line)) {
            builder.append(line).append("\n");
            line = reader.readLine();
        }
        return builder.toString();
    }
}
