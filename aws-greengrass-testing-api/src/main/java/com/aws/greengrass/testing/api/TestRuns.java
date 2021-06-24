/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.api;

import com.aws.greengrass.testing.api.model.TestRun;

import java.util.List;

public interface TestRuns {
    List<TestRun> tracking();

    void track(TestRun run);
}
