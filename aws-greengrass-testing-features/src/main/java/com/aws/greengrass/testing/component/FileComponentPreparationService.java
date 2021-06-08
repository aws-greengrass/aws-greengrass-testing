package com.aws.greengrass.testing.component;

import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.resources.AWSResources;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileComponentPreparationService extends RecipeComponentPreparationService {
    @Inject
    public FileComponentPreparationService(
            AWSResources resources,
            @Named(JacksonModule.YAML) ObjectMapper mapper,
            TestContext testContext,
            GreengrassContext greengrassContext,
            ComponentOverrides overrides) {
        super(value -> Files.newInputStream(Paths.get(value)),
                resources, mapper, testContext, greengrassContext, overrides);
    }
}
