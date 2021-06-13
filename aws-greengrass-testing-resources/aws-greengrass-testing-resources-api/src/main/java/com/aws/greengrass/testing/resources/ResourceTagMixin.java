package com.aws.greengrass.testing.resources;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface ResourceTagMixin<T> {
    T convertTag(Map.Entry<String, String> entry);

    default Collection<T> convertTags(Map<String, String> tags) {
        return tags.entrySet().stream().map(this::convertTag).collect(Collectors.toList());
    }
}
