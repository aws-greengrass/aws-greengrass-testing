/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.resources.iam;

import com.aws.greengrass.testing.resources.ResourceTagMixin;
import software.amazon.awssdk.services.iam.model.Tag;

import java.util.Map;

interface IamTaggingMixin extends ResourceTagMixin<Tag> {
    default Tag convertTag(Map.Entry<String, String> tag) {
        return Tag.builder().key(tag.getKey()).value(tag.getValue()).build();
    }
}
