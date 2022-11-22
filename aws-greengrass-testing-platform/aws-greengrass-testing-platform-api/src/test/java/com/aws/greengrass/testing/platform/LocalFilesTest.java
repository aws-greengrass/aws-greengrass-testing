/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.exception.CopyException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.util.FileUtils;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class LocalFilesTest {
    private static final Path dummyFile = Paths.get("./dummyDirectory/dummyFile.txt");
    private static final Path filePath = Paths.get("./dummyDirectory");
    private static final String dummyText = "dummyText";
    @Mock
    Device localDevice;

    @InjectMocks
    LocalFiles localFiles;

    @BeforeEach
    public void setup() {
        localFiles = Mockito.spy(new LocalFiles(localDevice));
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.recursivelyDelete(filePath);
    }

    @Test
    void GIVEN_some_filePath_WHEN_calling_makeDirectories_THEN_the_directory_exists() {
        localFiles.makeDirectories(filePath);

        assertTrue(Files.exists(filePath));
    }

    @Test
    void GIVEN_some_filePath_WHEN_listingContents_THEN_return_list_of_paths() throws IOException{
        localFiles.makeDirectories(filePath);
        FileWriter writer = new FileWriter(dummyFile.toString());
        writer.write(dummyText);
        writer.close();

        assertEquals(Arrays.asList(dummyFile), localFiles.listContents(filePath));
    }

    @Test
    void GIVEN_some_file_WHEN_file_exists_THEN_return_file_bytes() throws IOException {
        localFiles.makeDirectories(filePath);
        FileWriter writer = new FileWriter(dummyFile.toString());
        writer.write(dummyText);
        writer.close();

        assertEquals(dummyText, new String(localFiles.readBytes(dummyFile), StandardCharsets.UTF_8));
    }

    @Test
    void GIVEN_some_filePath_WHEN_calling_delete_THEN_the_directory_does_not_exist() throws IOException {
        localFiles.makeDirectories(filePath);
        FileWriter writer = new FileWriter(dummyFile.toString());
        writer.write(dummyText);
        writer.close();

        assertTrue(Files.exists(filePath));

        localFiles.delete(filePath);

        assertFalse(Files.exists(filePath));
    }

    @Test
    void GIVEN_some_filePath_WHEN_it_does_not_exist_THEN_listDirectories_throws_exception() {
        assertThrows(CommandExecutionException.class,() -> localFiles.listContents(filePath));
    }

    @Test
    void GIVEN_some_filePath_WHEN_it_does_not_exist_THEN_readBytes_throws_exception() {
        assertThrows(CommandExecutionException.class,() -> localFiles.readBytes(dummyFile));
    }

    @Test
    void GIVEN_some_filePath_WHEN_calling_format_THEN_return_filePathString() {
        assertEquals(filePath.toString(), localFiles.format(filePath));
    }
}
