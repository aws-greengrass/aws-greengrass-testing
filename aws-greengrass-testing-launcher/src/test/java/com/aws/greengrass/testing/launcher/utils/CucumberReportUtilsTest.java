/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher.utils;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.launcher.ParallelizationConfig;

import com.aws.greengrass.testing.launcher.TestLauncherParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class CucumberReportUtilsTest {
    @Mock
    ParallelizationConfig parallelizationConfig;

    @Mock
    ParameterValues parameterValues;

    @InjectMocks
    CucumberReportUtils cucumberReportUtils;

    private Path emptyCucumberResourceDirectory =
            Paths.get("src", "test", "resources", "com.aws.greengrass.testing.launcher", "utils", "empty_cucumber_report");

    private Path validCucumberresourceDirectory =
            Paths.get("src", "test", "resources", "com.aws.greengrass.testing.launcher", "utils", "valid_cucumber_report");

    private String emptyCucumberResourceAbsolutePath = emptyCucumberResourceDirectory.toFile().getAbsolutePath();

    private String validCucumberResourceAbsolutePath = validCucumberresourceDirectory.toFile().getAbsolutePath();

    @Test
    void GIVEN_noCucumberReport_WHEN_parseDryRunCucumberReport_THEN_throwIOException () {
        Mockito.when(parameterValues.getString(TestLauncherParameters.TEST_RESULTS_PATH)).thenReturn(Optional.of("dummyValue"));
        assertThrows(IOException.class,
                () -> cucumberReportUtils.parseDryRunCucumberReport(parallelizationConfig, parameterValues));
    }

    @Test
    void GIVEN_emptyCucumberReport_WHEN_parseDryRunCucumberReport_THEN_returnEmptyList () throws IOException {
        Mockito.when(parameterValues.getString(TestLauncherParameters.TEST_RESULTS_PATH)).thenReturn(Optional.of(emptyCucumberResourceAbsolutePath));
        assertEquals(0,
                cucumberReportUtils.parseDryRunCucumberReport(parallelizationConfig, parameterValues).size());
    }

    @Test
    void GIVEN_validCucumberReport_WHEN_parseDryRunCucumberReport_THEN_returnUriPoolList () throws IOException {
        Mockito.when(parameterValues.getString(TestLauncherParameters.TEST_RESULTS_PATH)).thenReturn(Optional.of(validCucumberResourceAbsolutePath));
        Mockito.when(parallelizationConfig.getBatchIndex()).thenReturn(0);
        Mockito.when(parallelizationConfig.getNumBatches()).thenReturn(1);
        List<String> uriPool = cucumberReportUtils.parseDryRunCucumberReport(parallelizationConfig, parameterValues);
        assertEquals(2, uriPool.size());
        assertEquals("classpath:greengrass/features/cloudComponent.feature:8", uriPool.get(0));
        assertEquals("classpath:greengrass/features/cloudComponent.feature:22", uriPool.get(1));
    }
}
