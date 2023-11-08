/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

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


@ScenarioScoped
public class SystemSteps {
    private static final Logger LOGGER = LogManager.getLogger(SystemSteps.class);
    private ArrayList<Double> ramList = new ArrayList<Double>();
    private ArrayList<Double> cpuList = new ArrayList<Double>();
    private SystemInfo si = new SystemInfo();
    private HardwareAbstractionLayer hal = si.getHardware();
    private CentralProcessor cpu = hal.getProcessor();
    private GlobalMemory ram = hal.getMemory();

    /**
     * Record the current CPU or RAM usage depending on the step.
     *
     * @param stat the statistic to record, must be CPU or RAM
     * @throws IllegalArgumentException when parameter is not CPU or RAM
     */
    @When("I record the device's {word} usage statistic")
    public void recordCpuOrRam(String stat) throws IllegalArgumentException {
        switch (stat) {
            case "CPU":
                double cpuLoad = cpu.getSystemCpuLoad(500) * 100;
                LOGGER.debug("System CPU load recorded as: " + cpuLoad + "%");
                cpuList.add(0, cpuLoad);
                break;
            case "RAM":
                double usedRam = (ram.getTotal() - ram.getAvailable()) / (1024 * 1024);
                LOGGER.debug("Used RAM recorded as: " + usedRam + " MB");
                ramList.add(0, usedRam);
                break;
            default:
                throw new IllegalArgumentException("Please specify either CPU or RAM to track.");
        }
    }

    /**
     * Check difference between last two records and verify it is below a threshold.
     *
     * @param stat the statistic to record, must be CPU or RAM
     * @param threshold the threshold to assert the difference is under, % for CPU and MB for RAM
     * @param units dummy parameter so that the step is picked up, always % for CPU and MB for RAM
     * @throws Exception when step fails due to exceeding the provided threshold or sample steps haven't run twice
     * @throws IllegalArgumentException when stat param is not CPU or RAM
     */
    @Then("the difference in the last two {word} samples is less than {int} {word}")
    public void checkSampleDiff(String stat, int threshold, String units) throws Exception, IllegalArgumentException {
        switch (stat) {
            case "CPU":
                if (cpuList.size() < 2) {
                    throw new Exception("Need at least two CPU samples first.");
                }
                double cpuDiff = cpuList.get(0) - cpuList.get(1);
                if (cpuDiff >= threshold) {
                    throw new Exception("CPU use was above the provided threshold.");
                }
                break;
            case "RAM":
                if (ramList.size() < 2) {
                    throw new Exception("Need at least two RAM samples first.");
                }
                double ramDiff = ramList.get(0) - ramList.get(1);
                if (ramDiff >= threshold) {
                    throw new Exception("RAM use was above the provided threshold.");
                }
                break;
            default:
                throw new IllegalArgumentException("Please specify either CPU or RAM to check.");
        }
    }
}
