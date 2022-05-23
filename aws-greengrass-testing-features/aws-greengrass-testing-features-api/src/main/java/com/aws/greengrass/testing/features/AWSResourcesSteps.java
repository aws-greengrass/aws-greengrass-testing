/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.google.inject.Inject;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;

@ScenarioScoped
public class AWSResourcesSteps implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger(AWSResourcesSteps.class);
    private final AWSResources resources;
    private final TestContext testContext;

    @Inject
    public AWSResourcesSteps(
            final AWSResources resources,
            final TestContext testContext) {
        this.resources = resources;
        this.testContext = testContext;
    }

    @After(order = 11000)
    @Override
    public void close() throws IOException {
        resources.close();
        testContext.close();
        LOGGER.info("Successfully removed externally created resources");
    }
}
