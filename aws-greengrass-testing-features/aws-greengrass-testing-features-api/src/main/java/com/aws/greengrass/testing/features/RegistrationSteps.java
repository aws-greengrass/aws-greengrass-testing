package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.model.GreengrassContext;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotRoleAliasSpec;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.aws.greengrass.testing.resources.iot.IotThingGroupSpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;

@ScenarioScoped
public class RegistrationSteps {
    private static final Logger LOGGER = LogManager.getLogger(RegistrationSteps.class);
    private static final String DEFAULT_CONFIG = "/nucleus/configs/basic_config.yaml";
    private final TestContext testContext;
    private final RegistrationContext registrationContext;
    private final AWSResourcesContext resourcesContext;
    private final GreengrassContext greengrassContext;
    private final AWSResources resources;
    private final IamSteps iamSteps;
    private final IotSteps iotSteps;
    private final Device device;
    private final Platform platform;

    @Inject
    RegistrationSteps(
            Device device,
            Platform platform,
            AWSResources resources,
            IamSteps iamSteps,
            IotSteps iotSteps,
            TestContext testContext,
            RegistrationContext registrationContext,
            GreengrassContext greengrassContext,
            AWSResourcesContext resourcesContext) {
        this.device = device;
        this.platform = platform;
        this.resources = resources;
        this.iamSteps = iamSteps;
        this.testContext = testContext;
        this.registrationContext = registrationContext;
        this.greengrassContext = greengrassContext;
        this.resourcesContext = resourcesContext;
        this.iotSteps = iotSteps;
    }

    @Given("my device is registered as a Thing using config {word}")
    public void registerAsThing(String configName) throws IOException {
        registerAsThing(configName, testContext.testId().idFor("ggc-group"));
    }

    @Given("my device is registered as a Thing")
    public void registerAsThing() throws IOException {
        registerAsThing(null);
    }

    private void registerAsThing(String configName, String thingGroupName) throws IOException {
        final String configFile = Optional.ofNullable(configName).orElse(DEFAULT_CONFIG);

        // TODO: move this into iot steps.
        IotThingSpec thingSpec = resources.create(IotThingSpec.builder()
                .thingName(testContext.testId().idFor("ggc-thing"))
                .addThingGroups(IotThingGroupSpec.of(thingGroupName))
                .createCertificate(true)
                .policySpec(resources.trackingSpecs(IotPolicySpec.class)
                        .filter(p -> p.policyName().equals(testContext.testId().idFor("ggc-iot-policy")))
                        .findFirst()
                        .orElseGet(iotSteps::createDefaultPolicy))
                .roleAliasSpec(IotRoleAliasSpec.builder()
                        .name(testContext.testId().idFor("ggc-role-alias"))
                        .iamRole(resources.trackingSpecs(IamRoleSpec.class)
                                .filter(s -> s.roleName().equals(testContext.testId().idFor("ggc-role")))
                                .findFirst()
                                .orElseGet(iamSteps::createDefaultIamRole)
                                .resource())
                        .build())
                .build());

        try (InputStream input = getClass().getResourceAsStream(configFile)) {
            setupConfig(
                    thingSpec.resource(),
                    thingSpec.roleAliasSpec(),
                    IoUtils.toUtf8String(input),
                    new HashMap<>());
        }
    }

    private void setupConfig(
            IotThing thing,
            IotRoleAliasSpec roleAliasSpec,
            String config,
            Map<String, String> additionalUpdatableFields) throws IOException {
        IotLifecycle iot = resources.lifecycle(IotLifecycle.class);
        Path configFilePath = testContext.testDirectory().resolve("config");
        Files.createDirectories(configFilePath);
        if (Objects.nonNull(thing)) {
            config = config.replace("{thing_name}", thing.thingName());
            config = config.replace("{iot_data_endpoint}", iot.dataEndpoint());
            config = config.replace("{iot_cred_endpoint}", iot.credentialsEndpoint());
            Files.write(testContext.testDirectory().resolve("privKey.key"),
                    thing.certificate().keyPair().privateKey().getBytes(StandardCharsets.UTF_8));
            Files.write(testContext.testDirectory().resolve("thingCert.crt"),
                    thing.certificate().certificatePem().getBytes(StandardCharsets.UTF_8));
        } else {
            additionalUpdatableFields.putIfAbsent("{thing_name}", "null");
            additionalUpdatableFields.putIfAbsent("{iot_data_endpoint}", "null");
            additionalUpdatableFields.putIfAbsent("{iot_cred_endpoint}", "null");
        }

        if (roleAliasSpec != null) {
            config = config.replace("{role_alias}", roleAliasSpec.resource().roleAlias());
        } else {
            additionalUpdatableFields.putIfAbsent("{role_alias}", "null");
        }

        config = config.replace("{proxy_url}",
                resourcesContext.proxyConfig().map(ProxyConfig::proxyUrl).orElse(""));
        config = config.replace("{aws_region}", resourcesContext.region().metadata().id());
        config = config.replace("{nucleus_version}", greengrassContext.version());
        config = config.replace("{env_stage}", resourcesContext.envStage());
        config = config.replace("{posix_user}", testContext.currentUser());
        config = config.replace("{data_plane_port}", Integer.toString(registrationContext.connectionPort()));

        Files.write(testContext.testDirectory().resolve("rootCA.pem"),
                registrationContext.rootCA().getBytes(StandardCharsets.UTF_8));
        Files.write(configFilePath.resolve("config.yaml"), config.getBytes(StandardCharsets.UTF_8));
        // Copy to where the nucleus will read it
        platform.files().makeDirectories(testContext.installRoot().getParent());
        device.copyTo(testContext.testDirectory(), testContext.installRoot());
    }
}
