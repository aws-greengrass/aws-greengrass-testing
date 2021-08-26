/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.platform.macos;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;
import com.aws.greengrass.testing.platform.UnixCommands;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MacosCommands extends UnixCommands {
    private static final Logger LOGGER = LogManager.getLogger(UnixCommands.class);

    MacosCommands(final Device device) {
        super(device);
    }

    @Override
    protected Map<Integer, List<Integer>> findDirectDescendants() throws CommandExecutionException {
        final Map<Integer, List<Integer>> pidMapping = new HashMap<>();
        final String result = executeToString(CommandInput.builder().line("ps a -o ppid,pid | more").build());
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
}
