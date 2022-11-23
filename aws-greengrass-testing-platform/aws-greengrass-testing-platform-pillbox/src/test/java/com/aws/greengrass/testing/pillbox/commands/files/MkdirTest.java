/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import com.aws.greengrass.testing.api.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MkdirTest {
    private static final String filePath = "dummyDirectory";
    private static final String recursiveFilePath = "dummyDirectory/dummyChildDirectory";

    CommandLine commandLine;

    @BeforeEach
    public void setup() {
        commandLine = new CommandLine(new Mkdir());
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.recursivelyDelete(Paths.get(filePath));
    }

    @Test
    void GIVEN_filePath_WHEN_calling_mkdir_THEN_directory_exists() {
        Integer returnValue = commandLine.execute(filePath);

        assertEquals(0, returnValue);
        assertTrue(Files.exists(Paths.get(filePath)));
    }

    @Test
    void GIVEN_recursive_filePath_WHEN_calling_mkdir_with_recursive_option_THEN_directory_exists() {
        Integer returnValue = commandLine.execute("-p", recursiveFilePath);

        assertEquals(0, returnValue);
        assertTrue(Files.exists(Paths.get(recursiveFilePath)));
    }

    @Test
    void GIVEN_recursive_filePath_WHEN_calling_mkdir_without_recursive_option_THEN_directory_does_not_exist() {
        Integer returnValue = commandLine.execute(recursiveFilePath);

        assertEquals(1, returnValue);
        assertFalse(Files.exists(Paths.get(recursiveFilePath)));
    }
    
}
