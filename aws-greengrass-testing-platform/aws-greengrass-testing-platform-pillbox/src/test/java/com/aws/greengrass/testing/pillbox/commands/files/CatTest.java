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
import java.io.FileWriter;
import java.io.IOException;

import com.aws.greengrass.testing.api.util.FileUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CatTest {
    private static final String badPath = "./doesNotExist";
    private static final String filePath = "./dummyDirectory";
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

        commandLine = new CommandLine(new Cat());
        
        Files.createDirectories(Paths.get(filePath));
        FileWriter writer = new FileWriter(dummyFile);
        writer.write(dummyText);
        writer.close();
    }

    @AfterEach
    public void cleanup() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);

        FileUtils.recursivelyDelete(Paths.get(filePath));
    }

    @Test
    void GIVEN_filePath_that_does_not_exist_WHEN_making_call_THEN_return_0_and_outputs(){
        Integer returnValue = commandLine.execute(badPath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals("File " + badPath.toString() + " does not exists\n", output);
    }

    @Test
    void GIVEN_regular_file_that_does_exist_WHEN_making_call_THEN_return_0_and_file_text(){
        Integer returnValue = commandLine.execute(dummyFile);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals(dummyText, output);
    }

    @Test
    void GIVEN_non_regular_file_WHEN_making_call_THEN_return_1_and_output_error(){
        Integer returnValue = commandLine.execute(filePath);
        String output = err.toString();

        assertEquals(1, returnValue);
        assertEquals("File '" + filePath.toString() + "' is not a file.\n", output);
    }
}
