/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.device.exception;


import com.aws.greengrass.testing.api.device.model.CommandInput;

public class CommandExecutionException extends RuntimeException {
    private static final long serialVersionUID = 2182900148201082390L;

    private int exitCode;
    private final CommandInput input;

    /**
     * Creates a {@link CommandExecutionException}.
     *
     * @param message The underlying reason for the failure.
     * @param exitCode The resulting exit code from the command.
     * @param input The source input provided by the customer.
     */
    public CommandExecutionException(String message, int exitCode, CommandInput input) {
        super(message);
        this.exitCode = exitCode;
        this.input = input;
    }

    public CommandExecutionException(Throwable ex, CommandInput input) {
        super(ex);
        this.input = input;
    }

    public int exitCode() {
        return exitCode;
    }
}
