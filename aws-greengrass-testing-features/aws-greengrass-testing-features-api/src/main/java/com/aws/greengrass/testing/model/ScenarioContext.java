package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResource;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import io.cucumber.guice.ScenarioScoped;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ScenarioScoped
public class ScenarioContext {
    private static final Logger LOGGER = LogManager.getLogger(ScenarioContext.class);
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
    private final Map<String, String> context;
    private final TestId testId;
    private final AWSResources resources;

    @Inject
    public ScenarioContext(
            final TestId testId,
            final AWSResources resources) {
        this.testId = testId;
        this.resources = resources;
        this.context = new ConcurrentHashMap<>();
    }

    public ScenarioContext put(final String key, final String value) {
        context.put(testId.idFor(key), value);
        return this;
    }

    private String pascalCase(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public String get(final String key) {
        String[] parts = key.split(":");
        switch (parts[0]) {
            case "aws.resources":
                // TODO: this is super fragile. This needs to be an SPI id
                if (parts.length >= 4) {
                    String type = pascalCase(parts[1]);
                    String resource = pascalCase(parts[2]);
                    try {
                        Class<?> targetClass = Class.forName("com.aws.greengrass.testing.resources."
                                + parts[1] + "." + type + resource + "Spec");
                        Method getter = targetClass.getMethod(parts[3]);
                        Object spec = resources.trackingSpecs((Class<? extends ResourceSpec>) targetClass)
                                .findFirst()
                                .get();
                        Object result = getter.invoke(spec);
                        if (Objects.nonNull(result)) {
                            return result.toString();
                        }
                    } catch (ClassNotFoundException
                            | NoSuchMethodException
                            | NoSuchElementException
                            | IllegalAccessException
                            | InvocationTargetException e) {
                        LOGGER.warn("Could not find a resource for {}. Falling back.",
                                Arrays.toString(parts));
                    }
                }
            default:
                return context.get(testId.idFor(key));
        }
    }

    public String applyInline(String content) {
        final Matcher matcher = PATTERN.matcher(content);
        String replacement = content;
        while (matcher.find()) {
            String value = get(matcher.group(1));
            replacement = replacement.replace(matcher.group(0), value);
        }
        return replacement;
    }
}
