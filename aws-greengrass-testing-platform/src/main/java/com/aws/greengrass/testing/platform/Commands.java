package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.nio.file.Path;
import java.util.List;

public interface Commands {
    byte[] execute(CommandInput input) throws CommandExecutionException;

    void makeExecutable(Path file) throws CommandExecutionException;

    int executeInBackground(CommandInput input) throws CommandExecutionException;

    List<Integer> findDescendants(int pid) throws CommandExecutionException;

    void kill(List<Integer> processIds) throws CommandExecutionException;

    default void killAll(int pid) throws CommandExecutionException {
        kill(findDescendants(pid));
    }
}
