package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ComponentPreparationService;
import com.aws.greengrass.testing.api.model.ComponentOverrideNameVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrideVersion;
import com.aws.greengrass.testing.api.model.ComponentOverrides;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassDeploymentSpec;
import com.aws.greengrass.testing.resources.greengrass.GreengrassV2Lifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.greengrassv2.model.ComponentDeploymentSpecification;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class DeploymentSteps {
    private static final Logger LOGGER = LogManager.getLogger(DeploymentSteps.class);
    private final AWSResources resources;
    private final ComponentPreparationService componentPreparation;
    private final ComponentOverrides overrides;
    private final TestContext testContext;
    private final GreengrassContext greengrassContext;
    private final WaitSteps waits;

    @Inject
    public DeploymentSteps(
            final AWSResources resources,
            final ComponentOverrides overrides,
            final TestContext testContext,
            final GreengrassContext greengrassContext,
            final ComponentPreparationService componentPreparation,
            final WaitSteps waits) {
        this.resources = resources;
        this.overrides = overrides;
        this.testContext = testContext;
        this.greengrassContext = greengrassContext;
        this.componentPreparation = componentPreparation;
        this.waits = waits;
    }

    @When("I create a Greengrass deployment with components")
    public void createDeployment(List<List<String>> componentNames) {
        IotThingSpec thingSpec = resources.trackingSpecs(IotThingSpec.class)
                .filter(thing -> thing.resource().thingName().equals(testContext.testId().idFor("ggc-thing")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A Greengrass deployment needs a valid target."));
        final Map<String, ComponentDeploymentSpecification> components = new HashMap<String, ComponentDeploymentSpecification>() {{
            put("aws.greengrass.Nucleus", ComponentDeploymentSpecification.builder()
                    .componentVersion(greengrassContext.version())
                    .build());
        }};
        componentNames.forEach(tuple -> {
            String name = tuple.get(0);
            String value = tuple.get(1);
            ComponentOverrideNameVersion.Builder overrideNameVersion = ComponentOverrideNameVersion.builder()
                    .from(overrides.component(name));
            String[] parts = value.split(":", 2);
            if (parts.length == 2) {
                overrideNameVersion.version(ComponentOverrideVersion.of(parts[0], parts[1]));
            } else {
                overrideNameVersion.version(ComponentOverrideVersion.of("cloud", parts[0]));
            }
            ComponentDeploymentSpecification.Builder builder = ComponentDeploymentSpecification.builder();
            componentPreparation.prepare(overrideNameVersion.build()).ifPresent(nameVersion -> {
                builder.componentVersion(nameVersion.version().value());
            });
            components.put(name, builder.build());
        });
        LOGGER.info("Creating deployment with components to {}: {}", thingSpec.thingName(), components);
        resources.create(GreengrassDeploymentSpec.builder()
                .deploymentName(testContext.testId().idFor("gg-deployment"))
                .thingArn(thingSpec.resource().thingArn())
                .putAllComponents(components)
                .build());
    }

    @Then("the Greengrass deployment is {word} on the device after {int} seconds")
    public void deploymentSucceeds(String status, int seconds) throws InterruptedException {
        assertTrue(waits.untilTrue(() -> effectivelyDeployedOnDevice(status), seconds, TimeUnit.SECONDS),
                "Deployment " + testContext.testId().idFor("gg-deployment") + " did not complete");
    }

    private boolean effectivelyDeployedOnDevice(String status) {
        GreengrassV2Lifecycle ggv2 = resources.lifecycle(GreengrassV2Lifecycle.class);
        String deploymentName = testContext.testId().idFor("gg-deployment");
        GreengrassDeploymentSpec ggcDeployment = ggv2.trackingSpecs(GreengrassDeploymentSpec.class)
                .filter(deployment -> deployment.deploymentName().equals(deploymentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find a deployment " + deploymentName));
        return ggv2.listDeviceDeployments(testContext.testId().idFor("ggc-thing")).effectiveDeployments().stream()
                .filter(deployment -> deployment.deploymentId().equals(ggcDeployment.resource().deploymentId()))
                .filter(deployment -> deployment.coreDeviceExecutionStatusAsString().equals(status))
                .findFirst()
                .isPresent();
    }
}
