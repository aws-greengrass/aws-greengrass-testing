/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.Greengrass;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;


public class GreengrassStepsTest {
    Greengrass greengrass = Mockito.mock(Greengrass.class);

    FileSteps fileSteps = Mockito.mock(FileSteps.class);

    @InjectMocks
    GreengrassSteps greengrassSteps = Mockito.spy(new GreengrassSteps(greengrass, fileSteps));

    @Test
    void GIVEN_a_greengrass_step_WHEN_some_greengrass_steps_got_invocated_THEN_the_expected_steps_are_invocated() {
        Mockito.doNothing().when(greengrass).install();
        Mockito.doNothing().when(fileSteps).checkFileExists(Mockito.any());
        Mockito.doNothing().when(greengrass).start();
        Mockito.doNothing().when(greengrass).stop();

        // Scenario 1: install
        greengrassSteps.install();
        Mockito.verify(greengrass, Mockito.times(1)).install();
        Mockito.verify(fileSteps, Mockito.times(1)).checkFileExists("logs/greengrass.log");

        // Scenario 2: start
        greengrassSteps.start();
        Mockito.verify(greengrass, Mockito.times(2)).install();
        Mockito.verify(greengrass, Mockito.times(1)).start();

        // Scenario 3: restart
        greengrassSteps.restart();
        Mockito.verify(greengrass, Mockito.times(1)).stop();
        Mockito.verify(greengrass, Mockito.times(2)).start();

        // Scenario 4: stop
        greengrassSteps.stop();
        Mockito.verify(greengrass, Mockito.times(2)).stop();

        // Scenario 5: close
        greengrassSteps.close();
        Mockito.verify(greengrass, Mockito.times(3)).stop();
    }
}
