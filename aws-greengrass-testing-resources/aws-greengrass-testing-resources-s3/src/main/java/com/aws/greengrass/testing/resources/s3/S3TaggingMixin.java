package com.aws.greengrass.testing.resources.s3;

import com.aws.greengrass.testing.resources.ResourceTagMixin;
import software.amazon.awssdk.services.s3.model.Tag;

import java.util.Map;

interface S3TaggingMixin extends ResourceTagMixin<Tag> {
    default Tag convertTag(Map.Entry<String, String> tag) {
        return Tag.builder().key(tag.getKey()).value(tag.getValue()).build();
    }
}
