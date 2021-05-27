package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.device.Device;
import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.AWSResourcesContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotRoleAliasSpec;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.aws.greengrass.testing.resources.iot.IotThingGroupSpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ScenarioScoped
public class RegistrationSteps {
    private static final String DEFAULT_CONFIG = "basic_config.yaml";
    private final TestContext testContext;
    private final RegistrationContext registrationContext;
    private final AWSResourcesContext resourcesContext;
    private final AWSResources resources;
    private final IamSteps iamSteps;
    private final Device device;

    @Inject
    public RegistrationSteps(
            Device device,
            AWSResources resources,
            IamSteps iamSteps,
            TestContext testContext,
            RegistrationContext registrationContext,
            AWSResourcesContext resourcesContext) {
        this.device = device;
        this.resources = resources;
        this.iamSteps = iamSteps;
        this.testContext = testContext;
        this.registrationContext = registrationContext;
        this.resourcesContext = resourcesContext;
    }

    @Given.Givens({
            @Given("my device is registered as a Thing"),
            @Given("my device is registered as a Thing using config {word}")
    })
    public void registerAsThing(String configName) throws IOException {
        registerAsThing(configName, testContext.testId().idFor("group"));
    }

    private void registerAsThing(String configName, String thingGroupName) throws IOException {
        final String configFile = Optional.ofNullable(configName).orElse(DEFAULT_CONFIG);
        IotRoleAliasSpec roleAliasSpec = IotRoleAliasSpec.builder()
                .name(testContext.testId().idFor("role-alias"))
                .iamRole(resources.trackingSpecs(IamRoleSpec.class)
                        .filter(s -> s.roleName().equals(testContext.testId().idFor("ggc-role")))
                        .findFirst()
                        .orElseGet(iamSteps::createDefaultIamRole)
                        .resource())
                .build();

        IotThingSpec thingSpec = IotThingSpec.builder()
                .thingName(testContext.testId().idFor("thing"))
                .addThingGroups(IotThingGroupSpec.of(thingGroupName))
                .createCertificate(true)
                .roleAliasSpec(roleAliasSpec)
                .build();

        IotThingSpec updatedSpec = resources.create(thingSpec);
        try (InputStream input = getClass().getResourceAsStream(DEFAULT_CONFIG)) {
            StringBuilder configBuilder = new StringBuilder();
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (Objects.nonNull(line)) {
                configBuilder.append(line);
                line = reader.readLine();
            }
            setupConfig(
                    updatedSpec.resource(),
                    roleAliasSpec,
                    configBuilder.toString(),
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
            Files.write(configFilePath.resolve("privKey.key"),
                    thing.certificate().keyPair().privateKey().getBytes(StandardCharsets.UTF_8));
            Files.write(configFilePath.resolve("thingCert.crt"),
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

        config = config.replace("{proxy_url}", resourcesContext.proxyConfig().map(ProxyConfig::proxyUrl).orElse(""));
        config = config.replace("{aws_region}", resourcesContext.region().metadata().id());
        config = config.replace("{nucleus_version}", "");
        config = config.replace("{env_stage}", resourcesContext.envStage());
        config = config.replace("{data_plane_port}", "8443");

        Files.write(configFilePath.resolve("rootCA.pem"), registrationContext.rootCA().getBytes(StandardCharsets.UTF_8));
        Files.write(configFilePath.resolve("config.yaml"), config.getBytes(StandardCharsets.UTF_8));
        // Copy to where the nucleus will read it
        device.copy(configFilePath, configFilePath.resolve("nucleus"));
    }
}
