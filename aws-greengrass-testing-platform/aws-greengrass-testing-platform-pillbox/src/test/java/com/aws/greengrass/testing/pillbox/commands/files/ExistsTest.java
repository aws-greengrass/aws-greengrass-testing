/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

import com.aws.greengrass.testing.api.util.FileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExistsTest {
    private static final String badPath = "./doesNotExist";
    private static final String filePath = "./dummyDirectory";

    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    CommandLine commandLine;

    @BeforeEach
    public void setup() throws IOException {
        out.reset();
        System.setOut(new PrintStream(out));

        commandLine = new CommandLine(new Exists());
        
        Files.createDirectories(Paths.get(filePath));
    }

    @AfterEach
    public void cleanup() throws IOException {
        System.setOut(originalOut);

        FileUtils.recursivelyDelete(Paths.get(filePath));
    }

    @Test
    void GIVEN_filePath_that_exists_WHEN_call_THEN_output_true() {
        Integer returnValue = commandLine.execute(filePath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals("true\n", output);
    }

    @Test
    void GIVEN_filePath_that_does_not_exist_WHEN_call_THEN_output_false() {
        Integer returnValue = commandLine.execute(badPath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals("false\n", output);
    }
}
