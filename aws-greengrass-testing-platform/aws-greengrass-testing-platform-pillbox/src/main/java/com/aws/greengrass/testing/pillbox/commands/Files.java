/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands;

import com.aws.greengrass.testing.pillbox.commands.files.Cat;
import com.aws.greengrass.testing.pillbox.commands.files.Exists;
import com.aws.greengrass.testing.pillbox.commands.files.Find;
import com.aws.greengrass.testing.pillbox.commands.files.Remove;
import picocli.CommandLine;

@CommandLine.Command(
        name = "files",
        description = "Platform independent file system interaction.",
        subcommands = { Cat.class, Find.class, Exists.class, Remove.class})
public class Files {
}
