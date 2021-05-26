package com.aws.greengrass.testing.launcher;

import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.console.options.Details;
import org.junit.platform.console.options.Theme;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.StringJoiner;

public final class TestLauncher {
    private static final String ENGINE = "cucumber";
    private static final String DEFAULT_GLUE_PATH = "com.aws.greengrass";
    private static final String DEFAULT_FEATURES = "greengrass/features";

    public static void main(String[] args) {
        final Path output;
        if (args.length > 0) {
            output = Paths.get(args[0]);
        } else {
            output = Paths.get("");
        }
        String tags = System.getProperty("tags");
        CommandLineOptions options = new CommandLineOptions();
        options.setTheme(Theme.UNICODE);
        options.setDetails(Details.TREE);
        options.setIncludedEngines(Arrays.asList(ENGINE));
        if (Objects.nonNull(tags)) {
            options.setIncludedTagExpressions(Arrays.asList(tags));
        }
        options.setSelectedClasspathResources(Arrays.asList(System.getProperty("feature.path", DEFAULT_FEATURES)));
        options.setConfigurationParameters(new HashMap<String, String>() {{
           put("cucumber.glue", System.getProperty("glue.package", DEFAULT_GLUE_PATH));
           put("cucumber.plugin", new StringJoiner(",")
                   .add("junit:" + output.toAbsolutePath().resolve("TEST-greengrass-results.xml").toString())
                   .toString());
        }});
    }
}
