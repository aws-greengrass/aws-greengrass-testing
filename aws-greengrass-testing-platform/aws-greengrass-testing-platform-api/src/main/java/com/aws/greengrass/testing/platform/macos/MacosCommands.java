/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.api.model.PillboxContext;
import com.aws.greengrass.testing.platform.UnixCommands;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class MacosCommands extends UnixCommands {

    MacosCommands(final Device device, final PillboxContext pillboxContext) {
        super(device, pillboxContext);
    }

    @VisibleForTesting
    Map<Integer, List<Integer>> findDirectDescendants() throws CommandExecutionException {
        final Map<Integer, List<Integer>> pidMapping = new HashMap<>();
        final String result = executeToString(CommandInput.builder().line("ps a -o ppid,pid").build());
        for (String pid : result.split("\\r?\\n")) {
            final List<Integer> childPids = new ArrayList<>();
            String[] pair = pid.split("\\s");
            if (pair[0].matches("\\d+") && pair[1].matches("\\d+")) {
                childPids.add(Integer.parseInt(pair[1]));
                if (pidMapping.containsKey(Integer.parseInt(pair[0]))) {
                    childPids.addAll(pidMapping.get(Integer.parseInt(pair[0])));
                }
                pidMapping.put(Integer.parseInt(pair[0]), childPids);
            }
        }
        return pidMapping;
    }

    @Override
    public List<Integer> findDescendants(int pid) throws CommandExecutionException {
        Map<Integer, List<Integer>> pidMap = findDirectDescendants();
        List<Integer> child = new ArrayList<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(pid);
        while (!queue.isEmpty()) {
            int process = queue.poll();
            child.add(process);
            if (pidMap.get(process) != null) {
                queue.addAll(pidMap.get(process));
            }
        }
        return child;
    }
}
