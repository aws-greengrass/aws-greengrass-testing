/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelizationConfigTest {
    @Test
    void GIVEN_batchIndexSet_WHEN_getBatchIndex_THEN_returnCorrectBatchIndex () {
        ParallelizationConfig parallelizationConfig = new ParallelizationConfig();
        parallelizationConfig.setBatchIndex(0);
        assertEquals(0, parallelizationConfig.getBatchIndex());
    }

    @Test
    void GIVEN_numBatchesSet_WHEN_getNumBatches_THEN_returnCorrectNumBatches () {
        ParallelizationConfig parallelizationConfig = new ParallelizationConfig();
        parallelizationConfig.setNumBatches(1);
        assertEquals(1, parallelizationConfig.getNumBatches());
    }
}
