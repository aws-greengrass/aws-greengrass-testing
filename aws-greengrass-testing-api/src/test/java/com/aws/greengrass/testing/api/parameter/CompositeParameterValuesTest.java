/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api.parameter;

import com.aws.greengrass.testing.api.ParameterValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class CompositeParameterValuesTest {
    @Mock
    ParameterValues parameterValues;

    Set<ParameterValues> parameterValuesSet;

    @BeforeEach
    public void setup() {
        parameterValuesSet = new HashSet<>();
        parameterValuesSet.add(parameterValues);

    }

    @Test
    void GIVEN_emptyParameterValues_WHEN_getParameterValue_THEN_return_empty(){
        ParameterValues parameterValues = ParameterValues.createDefault();
        Set<ParameterValues> set = new HashSet<>();
        set.add(parameterValues);
        CompositeParameterValues compositeParameterValues = new CompositeParameterValues(set);
        assertTrue(!compositeParameterValues.get("test").isPresent());
    }

    @Test
    void GIVEN_validParameterValues_WHEN_getParameterValue_THEN_return_parameterValue(){
        CompositeParameterValues compositeParameterValues = new CompositeParameterValues(parameterValuesSet);
        Mockito.doReturn(Optional.of("test")).when(parameterValues).get(Mockito.any());
        assertTrue(compositeParameterValues.get("test").isPresent());
    }
}
