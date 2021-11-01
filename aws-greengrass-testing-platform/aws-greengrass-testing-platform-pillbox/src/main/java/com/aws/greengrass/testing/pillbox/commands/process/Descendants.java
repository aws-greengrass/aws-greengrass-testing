/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.pillbox.commands.process;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandLine.Command(
        name = "descendants",
        description = "List all of the descendant processes for a single process")
public class Descendants implements Callable<Integer> {
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile("^PPid:\\s*(\\d+)");

    @CommandLine.Parameters(index = "0")
    private int pid;

    private Map<Integer, List<Integer>> findDirectDescendants() throws IOException {
        final Map<Integer, List<Integer>> pidToDirect = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("/proc"))) {
            for (Path child : files) {
                int childPid;
                try {
                    childPid = Integer.parseInt(child.getFileName().toString());
                } catch (NumberFormatException nfe) {
                    continue;
                }
                pidToDirect.computeIfAbsent(childPid, ArrayList::new);
                Path status = child.resolve("status");
                if (Files.exists(status)) {
                    Files.readAllLines(status, StandardCharsets.UTF_8).forEach(line -> {
                        Matcher matcher = PROCESS_ID_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            int parentId = Integer.parseInt(matcher.group(1));
                            pidToDirect.compute(parentId, (ppid, pids) -> {
                                List<Integer> cids = Optional.ofNullable(pids).orElseGet(ArrayList::new);
                                cids.add(childPid);
                                return cids;
                            });
                        }
                    });
                }
            }
            return pidToDirect;
        }
    }

    @Override
    public Integer call() throws Exception {
        Map<Integer, List<Integer>> processMap = findDirectDescendants();
        Queue<Integer> remaining = new LinkedList<>();
        remaining.offer(pid);
        while (!remaining.isEmpty()) {
            int parentId = remaining.poll();
            System.out.println(parentId);
            if (processMap.containsKey(parentId)) {
                processMap.get(parentId).forEach(remaining::offer);
            }
        }
        return 0;
    }
}
