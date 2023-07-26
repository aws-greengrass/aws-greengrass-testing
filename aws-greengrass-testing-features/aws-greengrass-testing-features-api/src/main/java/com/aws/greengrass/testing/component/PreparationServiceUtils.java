/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

class PreparationServiceUtils {

    /**
     * Checks is artifact with schema classpath or file exists.
     *
     * @param uri URI to check is exist of not
     * @return true when artifact is not classpath or file, or exists
     * @throws IOException when IO errors occured
     */
    protected boolean isArtifactExists(String uri) throws IOException {
        String[] parts = uri.split(":", 2);
        switch (parts[0]) {
            case "classpath":
                InputStream stream = getClass().getResourceAsStream(parts[1]);
                if (stream == null) {
                    return false;
                }
                stream.close();
                break;
            case "file":
                return Files.exists(Paths.get(parts[1]));
            default:
                break;
        }

        return true;
    }
}
