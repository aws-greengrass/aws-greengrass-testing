/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.ResourceSpec;
import io.cucumber.guice.ScenarioScoped;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

@ScenarioScoped
public class ScenarioContext {
    private static final Logger LOGGER = LogManager.getLogger(ScenarioContext.class);
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
    private final Map<String, String> context;
    private final AWSResources resources;
    private final TestContext testContext;

    /**
     * Creates a {@link ScenarioContext} with {@link TestContext} and {@link AWSResources} mapped to scenario.
     *
     * @param testContext Scenario unique {@link TestContext}
     * @param resources Scenario unique {@link AWSResources}
     */
    @Inject
    public ScenarioContext(
            final TestContext testContext,
            final AWSResources resources) {
        this.testContext = testContext;
        this.resources = resources;
        this.context = new ConcurrentHashMap<>();
        put("test.id", testId.id());
    }

    public ScenarioContext put(final String key, final String value) {
        context.put(testContext.testId().idFor(key), value);
        return this;
    }

    private String pascalCase(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Gets the string value for the tracking key.
     *
     * @param key Tracking key to context value
     * @return
     */
    @SuppressWarnings("unchecked")
    public String get(final String key) {
        String[] parts = key.split(":");
        if (parts[0].equals("aws.resources") && parts.length >= 4) {
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
        } else if (parts[0].equals("test.context") && parts.length >= 2) {
            if (parts[1].equals("installRoot")) {
                return testContext.installRoot().toString();
            }
        }
        return context.get(testContext.testId().idFor(key));
    }

    /**
     * Replaces the content with entities found within the {@link ScenarioContext}.
     *
     * @param content Full content to replace with values found in the context
     * @return
     */
    public String applyInline(String content) {
        final Matcher matcher = PATTERN.matcher(content);
        String replacement = content;
        while (matcher.find()) {
            String value = Objects.requireNonNull(get(matcher.group(1)),
                    "Could not find a scenario context key for " + matcher.group(1));
            replacement = replacement.replace(matcher.group(0), value);
        }
        return replacement;
    }
}
