/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.model;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComponentOverridesModelTest {
    private String mockComponentName = "mock_component";
    private String mockType = "mock_type";
    private String mockValue = "mock_value";
    private ComponentOverridesModel componentOverridesModel = new ComponentOverridesModel() {
        @Nullable
        @Override
        public String bucketName() {
            return null;
        }

        @Override
        public Map<String, ComponentOverrideVersion> overrides() {
            Map<String, ComponentOverrideVersion> componentOverrideVersionMap = new HashMap<>();

            componentOverrideVersionMap.put(mockComponentName, ComponentOverrideVersion.builder().type(mockType).value(mockValue).build());
            return componentOverrideVersionMap;
        }
    };

    @Test
    void GIVEN_validComponentOverrideVersion_WHEN_overrideComponent_THEN_returnComponentOverrideNameVersion() {
        Optional<ComponentOverrideNameVersion> componentOverrideNameVersion =
                componentOverridesModel.component(mockComponentName);
        assertEquals(mockComponentName,componentOverrideNameVersion.get().name());
        assertEquals(mockType,componentOverrideNameVersion.get().version().type());
        assertEquals(mockValue,componentOverrideNameVersion.get().version().value());
    }
}
