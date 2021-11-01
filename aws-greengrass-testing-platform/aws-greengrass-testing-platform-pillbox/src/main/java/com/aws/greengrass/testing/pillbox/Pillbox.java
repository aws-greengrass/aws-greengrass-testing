/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox;


import com.aws.greengrass.testing.pillbox.commands.Files;
import com.aws.greengrass.testing.pillbox.commands.Process;
import picocli.CommandLine;

@CommandLine.Command(
        name = "com/aws/greengrass/testing/pillbox",
        version = "1.0.0",
        description = "A platform independent utility for interacting with the OS.",
        subcommands = { Files.class, Process.class })
public class Pillbox {
    @CommandLine.Option(
            names = {"-h", "--help"},
            description = "Displays help usage information",
            scope = CommandLine.ScopeType.INHERIT,
            usageHelp = true)
    private boolean help;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Pillbox()).execute(args));
    }
}
