/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher;

public class ParallelizationConfig {

    private Integer batchIndex;
    private Integer numBatches;

    public Integer getBatchIndex() {
        return batchIndex;
    }

    public void setBatchIndex(Integer batchIndex) {
        this.batchIndex = batchIndex;
    }

    public Integer getNumBatches() {
        return numBatches;
    }

    public void setNumBatches(Integer numBatches) {
        this.numBatches = numBatches;
    }
}
