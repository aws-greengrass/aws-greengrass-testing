/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.launcher.utils;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.launcher.ParallelizationConfig;
import com.aws.greengrass.testing.launcher.TestLauncherParameters;
import io.cucumber.core.internal.gherkin.deps.com.google.gson.Gson;
import io.cucumber.core.internal.gherkin.deps.com.google.gson.JsonArray;
import io.cucumber.core.internal.gherkin.deps.com.google.gson.JsonElement;
import io.cucumber.core.internal.gherkin.deps.com.google.gson.JsonObject;
import io.cucumber.core.internal.gherkin.deps.com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class CucumberReportUtils {
    private static final Logger LOGGER = LogManager.getLogger(CucumberReportUtils.class);
    private static final String CUCUMBER_REPORT = "cucumber.json";
    private static final String URI = "uri";
    private static final String LINE = "line";
    private static final String ELEMENT = "elements";
    private static final String BACKGROUND = "background";
    private static final String TYPE = "type";
    private List<String> uriPool;

    @Inject
    public CucumberReportUtils() {
        this.uriPool = new ArrayList<>();
    }

    @SuppressWarnings("MissingJavadocMethod")
    public List<String> parseDryRunCucumberReport(ParallelizationConfig parallelConfig, ParameterValues values)
            throws IOException {
        Path output = Paths.get(values.getString(TestLauncherParameters.TEST_RESULTS_PATH).orElse(""));
        Path cucumberReport = output.toAbsolutePath().resolve(CUCUMBER_REPORT);
        if (!new File(cucumberReport.toString()).isFile()) {
            throw new FileNotFoundException(cucumberReport + " is not found");
        }

        Gson gson = new Gson();
        String objectStr;
        try (FileReader fileReader = new FileReader(cucumberReport.toString())) {
            objectStr = new JsonParser().parse(fileReader).toString();
        }
        JsonArray features = gson.fromJson(objectStr, JsonArray.class);
        int offset = 0;
        for (int i = 0; i < features.size(); i++) {
            JsonObject feature = features.get(i).getAsJsonObject();
            String uri = feature.get(CucumberReportUtils.URI).getAsString();
            for (JsonElement json : feature.get(CucumberReportUtils.ELEMENT).getAsJsonArray()) {
                JsonObject element = json.getAsJsonObject();
                if (element.get(CucumberReportUtils.TYPE).getAsString().equals(CucumberReportUtils.BACKGROUND)) {
                    continue;
                }
                String line = element.get(CucumberReportUtils.LINE).getAsString();

                if (offset % parallelConfig.getNumBatches() == parallelConfig.getBatchIndex()) {
                    // e.g. classpath:greengrass/features/cloudComponent.feature:6
                    uriPool.add(uri.split(":")[0] + ":" + uri.split(":")[1] + ":" + line);
                }
                offset++;
            }
        }

        LOGGER.debug("Parallelization mechanism is as following:");
        LOGGER.debug("Batch[{}] test case group contains: {} cases in total, they are: \n * {}",
                parallelConfig.getBatchIndex(), uriPool.size(), uriPool.toString());

        return uriPool;
    }
}
