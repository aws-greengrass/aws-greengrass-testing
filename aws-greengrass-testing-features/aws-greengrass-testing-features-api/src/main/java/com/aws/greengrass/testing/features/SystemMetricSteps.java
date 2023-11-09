/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.model.ScenarioContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.ArrayList;
import javax.inject.Inject;


@ScenarioScoped
public class SystemMetricSteps {
    private static final Logger LOGGER = LogManager.getLogger(SystemMetricSteps.class);
    private ArrayList<Double> ramList = new ArrayList<Double>();
    private ArrayList<Double> cpuList = new ArrayList<Double>();
    private SystemInfo si;
    private HardwareAbstractionLayer hal;
    private CentralProcessor cpu;
    private GlobalMemory ram;
    private ScenarioContext scenarioContext;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public SystemMetricSteps(ScenarioContext scenarioContext) {
        this.si = new SystemInfo();
        this.hal = si.getHardware();
        this.cpu = hal.getProcessor();
        this.ram = hal.getMemory();
        this.scenarioContext = scenarioContext;
    }

    /**
     * Record the current CPU or RAM usage depending on the step.
     *
     * @param stat the statistic to record, must be CPU or RAM
     * @param statKey the key to store the statistic under in scenario context
     * @throws IllegalArgumentException when parameter is not CPU or RAM, or when the units do not match
     */
    @When("I record the device's {word} usage statistic as {word}")
    public void recordCpuOrRamInContext(String stat, String statKey) throws IllegalArgumentException {
        switch (stat) {
            case "CPU":
                // 500 is the time delay in millis that we record CPU load with. Can be changed to a better default.
                double cpuLoad = cpu.getSystemCpuLoad(500) * 100;
                LOGGER.debug("System CPU load recorded as: {}%", cpuLoad);
                scenarioContext.put(statKey, Double.toString(cpuLoad));
                break;
            case "RAM":
                double usedRam = (double) (ram.getTotal() - ram.getAvailable()) / (1024 * 1024);
                LOGGER.debug("Used RAM recorded as: {} MB", usedRam);
                scenarioContext.put(statKey, Double.toString(usedRam));
                break;
            default:
                throw new IllegalArgumentException("Please specify either CPU or RAM to track.");
        }
    }

    /**
     * Check difference between last two records and verify it is below a threshold.
     *
     * @param stat the statistic to record, must be CPU or RAM
     * @param statKey1 the first key to check in scenarioContext, the sample expected to be lesser
     * @param statKey2 the second key to check in scenarioContext, the sample expected to be greater
     * @param threshold the threshold to assert the difference is under, % for CPU and MB for RAM
     * @param units dummy parameter so that the step is picked up, always % for CPU and MB for RAM
     * @throws Exception when step fails due to exceeding the provided threshold or sample steps haven't run twice
     * @throws IllegalArgumentException when stat param is not CPU or RAM, or when the units do not match
     */
    @Then("the increase in the {word} usage from {word} to {word} is less than {int} {word}")
    public void checkSampleDiff(String stat,
                                String statKey1,
                                String statKey2,
                                int threshold,
                                String units) throws Exception, IllegalArgumentException {
        switch (stat) {
            case "CPU":
                if (!units.equals("percent")) {
                    throw new IllegalArgumentException("Please use percent as the unit of measurement for CPU usage.");
                }
                double cpuSample2 = Double.parseDouble(scenarioContext.get(statKey2));
                double cpuSample1 = Double.parseDouble(scenarioContext.get(statKey1));
                double cpuDiff = cpuSample2 - cpuSample1;
                if (cpuDiff >= threshold) {
                    throw new Exception("CPU use was above the provided threshold.");
                }
                break;
            case "RAM":
                if (!units.equals("MB")) {
                    throw new IllegalArgumentException("Please use MB as the unit of measurement for RAM comparisons.");
                }
                double ramSample2 = Double.parseDouble(scenarioContext.get(statKey2));
                double ramSample1 = Double.parseDouble(scenarioContext.get(statKey1));
                double ramDiff = ramSample2 - ramSample1;
                if (ramDiff >= threshold) {
                    throw new Exception("RAM use was above the provided threshold.");
                }
                break;
            default:
                throw new IllegalArgumentException("Please specify either CPU or RAM to check.");
        }
    }
}
