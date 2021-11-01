/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands;

import com.aws.greengrass.testing.pillbox.commands.process.Descendants;
import picocli.CommandLine;

@CommandLine.Command(
        name = "process",
        description = "Platform independent process management utility",
        subcommands = Descendants.class)
public class Process {
}
