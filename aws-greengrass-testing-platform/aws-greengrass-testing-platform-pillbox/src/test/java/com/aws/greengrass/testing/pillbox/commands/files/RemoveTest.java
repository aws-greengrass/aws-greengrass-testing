/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.files;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.FileWriter;
import java.io.IOException;

import com.aws.greengrass.testing.api.util.FileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RemoveTest {
    private static final String badPath = "./doesNotExist";
    private static final String dummyDirectory = "./dummyDirectory";
    private static final String dummyFile = "./dummyDirectory/dummyFile.txt";
    private static final String dummyText = "dummyText";

    final PrintStream originalOut = System.out;
    final PrintStream originalErr = System.err;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();

    CommandLine commandLine;

    @BeforeEach
    public void setup() throws IOException {
        out.reset();
        err.reset();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        commandLine = new CommandLine(new Remove());
        
        Files.createDirectories(Paths.get(dummyDirectory));
        FileWriter writer = new FileWriter(dummyFile);
        writer.write(dummyText);
        writer.close();
    }

    @AfterEach
    public void cleanup() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);

        FileUtils.recursivelyDelete(Paths.get(dummyDirectory));
    }

    @Test
    void GIVEN_path_of_directory_WHEN_calling_remove_with_recursive_flag_THEN_directory_no_longer_exists() {
        assertTrue(Files.exists(Paths.get(dummyDirectory)));

        Integer returnValue = commandLine.execute("-r", dummyDirectory);

        assertEquals(0, returnValue);
        assertFalse(Files.exists(Paths.get(dummyDirectory)));
    }

    @Test
    void GIVEN_path_of_directory_WHEN_calling_remove_without_recursive_flag_THEN_fail() {
        Integer returnValue = commandLine.execute(dummyDirectory);
        String output = err.toString();

        assertEquals(1, returnValue);
        assertEquals("cannot remove '" + dummyDirectory + "': Is a directory\n", output);
    }

    @Test
    void GIVEN_path_of_file_WHEN_calling_remove_THEN_file_no_longer_exists() {
        assertTrue(Files.exists(Paths.get(dummyFile)));
        
        Integer returnValue = commandLine.execute(dummyFile);

        assertEquals(0, returnValue);
        assertFalse(Files.exists(Paths.get(dummyFile)));
        assertTrue(Files.exists(Paths.get(dummyDirectory)));
    }

    @Test
    void GIVEN_directory_path_that_does_not_exist_WHEN_calling_remove_THEN_fail() {
        assertFalse(Files.exists(Paths.get(badPath)));
        Integer returnValue = commandLine.execute(badPath);

        assertEquals(1, returnValue);
    }
}
