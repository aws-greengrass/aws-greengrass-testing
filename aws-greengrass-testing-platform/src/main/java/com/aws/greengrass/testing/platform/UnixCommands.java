package com.aws.greengrass.testing.platform;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.device.exception.CommandExecutionException;
import com.aws.greengrass.testing.api.device.model.CommandInput;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class UnixCommands implements Commands {
    private static final Pattern PID_REGEX = Pattern.compile("^(\\d*)\\s");
    protected final Device device;

    public UnixCommands(final Device device) {
        this.device = device;
    }

    @Override
    public byte[] execute(CommandInput input) throws CommandExecutionException {
        final StringJoiner joiner = new StringJoiner(" ").add(input.line());
        Optional.ofNullable(input.args()).ifPresent(args -> args.forEach(joiner::add));
        return device.execute(CommandInput.builder()
                .line("sh")
                .addArgs("-c", joiner.toString())
                .input(input.input())
                .timeout(input.timeout())
                .build());
    }

    @Override
    public int executeInBackground(CommandInput input) throws CommandExecutionException {
        final byte[] rawBytes = execute(CommandInput.builder()
                .from(input)
                .addArgs("2>&1", "&", "echo", "$!")
                .build());
        return Integer.parseInt(new String(rawBytes, StandardCharsets.UTF_8).trim());
    }

    @Override
    public List<Integer> findProcesses(String ofType) throws CommandExecutionException {
        final byte[] rawBytes = execute(CommandInput.of("ps -ef | grep -i " + ofType));
        final String result = new String(rawBytes, StandardCharsets.UTF_8);
        System.out.println(result);
        return Arrays.stream(result.split("\\n")).map(String::trim).flatMap(line -> {
            final Matcher matcher = PID_REGEX.matcher(line);
            final List<Integer> pids = new ArrayList<>();
            while (matcher.find()) {
                pids.add(Integer.parseInt(matcher.group(1).trim()));
            }
            return pids.stream();
        }).collect(Collectors.toList());
    }

    @Override
    public void kill(List<Integer> processIds) throws CommandExecutionException {
        processIds.forEach(id -> execute(CommandInput.of("kill " + id)));
    }
}
