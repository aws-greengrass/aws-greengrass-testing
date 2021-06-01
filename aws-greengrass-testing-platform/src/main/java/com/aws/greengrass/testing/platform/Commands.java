package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.util.List;

public interface Commands {
    byte[] execute(CommandInput input) throws CommandExecutionException;

    int executeInBackground(CommandInput input) throws CommandExecutionException;

    List<Integer> findProcesses(String ofType) throws CommandExecutionException;

    void kill(List<Integer> processIds) throws CommandExecutionException;

    default void killAll(String ofType) throws CommandExecutionException {
        kill(findProcesses(ofType));
    }
}
