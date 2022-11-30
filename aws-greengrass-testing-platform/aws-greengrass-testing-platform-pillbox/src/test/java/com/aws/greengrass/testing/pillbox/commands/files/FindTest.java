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
import com.aws.greengrass.testing.pillbox.commands.files.Find.Type;
import java.lang.IllegalArgumentException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class FindTest {
    private static final String badPath = Paths.get(".").resolve("doesNotExist").toString();
    private static final String filePath = Paths.get(".").resolve("dummyDirectory").toString();
    private static final String dummyFile = Paths.get(".").resolve("dummyDirectory").resolve("dummyFile.txt").toString();
    private static final String dummyText = "dummyText";

    final PrintStream originalOut = System.out;
    final PrintStream originalErr = System.err;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();

    CommandLine commandLine;

    @BeforeEach
    public void setup() throws IOException {
        out.reset();
        System.setOut(new PrintStream(out));

        commandLine = new CommandLine(new Find());
        
        Files.createDirectories(Paths.get(filePath));
        FileWriter writer = new FileWriter(dummyFile);
        writer.write(dummyText);
        writer.close();
    }

    @AfterEach
    public void cleanup() throws IOException {
        System.setOut(originalOut);

        FileUtils.recursivelyDelete(Paths.get(filePath));
    }

    @Test
    void GIVEN_filePath_that_does_not_exist_WHEN_call_THEN_return_0(){
        Integer returnValue = commandLine.execute(badPath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals(
            ("File " + badPath + " does not exists").replaceAll("\\s+",""),
            output.replaceAll("\\s+","")
        );
    }

    @Test
    void GIVEN_directory_that_does_exist_WHEN_call_THEN_output_filePath() {
        Integer returnValue = commandLine.execute(filePath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals(
            (filePath + "\n" + dummyFile + "\n").replaceAll("\\s+",""),
            output.replaceAll("\\s+","")
        );
    }

    @Test
    void GIVEN_file_that_does_exist_WHEN_call_THEN_output_filePath() {
        Integer returnValue = commandLine.execute(filePath);
        String output = out.toString();

        assertEquals(0, returnValue);
        assertEquals(
            (filePath + "\n" + dummyFile + "\n").replaceAll("\\s+",""),
            output.replaceAll("\\s+","")
        );
    }

    @Test
    void GIVEN_file_Type_code_string_WHEN_calling_fromCode_THEN_return_proper_Type() {
        Type type = Type.fromCode("f");

        assertEquals("f", type.code());
        assertNotEquals("d", type.code());

        assertEquals(Type.FILE, type);
        assertNotEquals(Type.DIRECTORY, type);
    }

    @Test
    void GIVEN_directory_Type_code_string_WHEN_calling_fromCode_THEN_return_proper_Type() {
        Type type = Type.fromCode("d");

        assertEquals("d", type.code());
        assertNotEquals("f", type.code());

        assertEquals(Type.DIRECTORY, type);
        assertNotEquals(Type.FILE, type);
    }

    @Test
    void GIVEN_invalid_Type_code_string_WHEN_calling_fromCode_THEN_throw_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Type.fromCode(""));
    }
}
