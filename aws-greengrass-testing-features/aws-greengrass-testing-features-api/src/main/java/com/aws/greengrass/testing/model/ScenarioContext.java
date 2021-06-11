package com.aws.greengrass.testing.model;

import com.aws.greengrass.testing.api.model.TestId;
import io.cucumber.guice.ScenarioScoped;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ScenarioScoped
public class ScenarioContext {
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
    private final Map<String, String> context;
    private final TestId testId;

    @Inject
    public ScenarioContext(final TestId testId) {
        this.testId = testId;
        this.context = new ConcurrentHashMap<>();
    }

    public ScenarioContext put(final String key, final String value) {
        context.put(testId.idFor(key), value);
        return this;
    }

    public String get(final String key) {
        return context.get(testId.idFor(key));
    }

    public String applyInline(String content) {
        final Matcher matcher = PATTERN.matcher(content);
        String replacement = content;
        while (matcher.find()) {
            String value = get(matcher.group(1));
            replacement = replacement.replaceFirst(matcher.group(0), value);
        }
        return replacement;
    }
}
