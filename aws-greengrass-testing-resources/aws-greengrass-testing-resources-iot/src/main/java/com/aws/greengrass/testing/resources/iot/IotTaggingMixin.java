package com.aws.greengrass.testing.resources.iot;

import com.aws.greengrass.testing.resources.ResourceTagMixin;
import software.amazon.awssdk.services.iot.model.Tag;

import java.util.Map;

interface IotTaggingMixin extends ResourceTagMixin<Tag> {
    @Override
    default Tag convertTag(Map.Entry<String, String> tag) {
        return Tag.builder().key(tag.getKey()).value(tag.getValue()).build();
    }
}
