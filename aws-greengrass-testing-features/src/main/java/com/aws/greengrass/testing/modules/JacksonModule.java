package com.aws.greengrass.testing.modules;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.auto.service.AutoService;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

@AutoService(Module.class)
public class JacksonModule extends AbstractModule {
    public static final String YAML = "yaml.mapper";

    @Provides
    @Singleton
    static ObjectMapper providesJsonMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Provides
    @Singleton
    @Named(YAML)
    static ObjectMapper providesYamlMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}
