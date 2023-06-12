/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.features;

import com.aws.greengrass.testing.api.ParameterValues;
import com.aws.greengrass.testing.api.model.ProxyConfig;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.FeatureParameters;
import com.aws.greengrass.testing.modules.HsmParameters;
import com.aws.greengrass.testing.modules.JacksonModule;
import com.aws.greengrass.testing.modules.exception.ModuleProvisionException;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.platform.Platform;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.greengrass.GreengrassCoreDeviceSpec;
import com.aws.greengrass.testing.resources.iam.IamLifecycle;
import com.aws.greengrass.testing.resources.iam.IamRole;
import com.aws.greengrass.testing.resources.iam.IamRoleSpec;
import com.aws.greengrass.testing.resources.iot.IotCertificate;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotRoleAliasSpec;
import com.aws.greengrass.testing.resources.iot.IotThing;
import com.aws.greengrass.testing.resources.iot.IotThingGroupSpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.KeyManagerFactory;

import static com.aws.greengrass.testing.modules.HsmParameters.ROOT_CA_PATH;
import static com.aws.greengrass.testing.util.EncryptionUtils.loadPrivateKeyPair;
import static com.aws.greengrass.testing.util.EncryptionUtils.loadX509Certificates;

@ScenarioScoped
public class RegistrationSteps {
    private static final Logger LOGGER = LogManager.getLogger(RegistrationSteps.class);
    private static final String DEFAULT_CONFIG = "/nucleus/configs/basic_config.yaml";
    private static final String DEFAULT_HSM_CONFIG = "/nucleus/configs/basic_hsm_config.yaml";
    private final TestContext testContext;
    private final RegistrationContext registrationContext;
    private final AWSResourcesContext resourcesContext;
    private final AWSResources resources;
    private final IamSteps iamSteps;
    private final IotSteps iotSteps;
    private final Platform platform;
    private final IamLifecycle iamLifecycle;
    private final ParameterValues parameterValues;
    private final FileSteps fileSteps;
    private final ObjectMapper mapper;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public RegistrationSteps(
            Platform platform,
            AWSResources resources,
            IamSteps iamSteps,
            IotSteps iotSteps,
            TestContext testContext,
            RegistrationContext registrationContext,
            AWSResourcesContext resourcesContext,
            IamLifecycle iamLifecycle,
            ParameterValues parameterValues,
            FileSteps fileSteps,
            @Named(JacksonModule.YAML) ObjectMapper objectMapper) {
        this.platform = platform;
        this.resources = resources;
        this.iamSteps = iamSteps;
        this.testContext = testContext;
        this.registrationContext = registrationContext;
        this.resourcesContext = resourcesContext;
        this.iotSteps = iotSteps;
        this.iamLifecycle = iamLifecycle;
        this.parameterValues = parameterValues;
        this.fileSteps = fileSteps;
        this.mapper = objectMapper;
    }

    /**
     * Entry point for newly registering a Greengrass device ona device.
     *
     * @param configName the config name to use for the base config
     * @throws IOException thrown when failing to read the config
     * @throws InterruptedException thread interrupted
     */
    @Given("my device is registered as a Thing using config {word}")
    public void registerAsThing(String configName) throws IOException, InterruptedException {
        // Already registered ... already installed
        if (!testContext.initializationContext().persistInstalledSoftware()) {
            registerAsThing(configName, testContext.testId().idFor("ggc-group"));
        }
    }

    /**
     * Doesn't register for PreInstalled case, registers device otherwise.
     * @throws IOException thrown when failing to read the config
     * @throws InterruptedException thread interrupted
     */
    @Given("my device is registered as a Thing")
    @SuppressWarnings("MissingJavadocMethod")
    public void registerAsThing() throws IOException, InterruptedException {
        if (!testContext.initializationContext().persistInstalledSoftware()) {
            registerAsThing(null);
        }

        if (testContext.initializationContext().persistInstalledSoftware()
                && testContext.hsmConfigured()) {
            checkHSMConfigForPreInstalled();
        }
    }

    @VisibleForTesting
    void registerAsThing(String configName, String thingGroupName) throws IOException, InterruptedException {
        final String configFile = Optional.ofNullable(configName).orElse(getDefaultConfigName());
        String tesRoleNameName = testContext.tesRoleName();
        Optional<IamRole> optionalIamRole = Optional.empty();
        if (!tesRoleNameName.isEmpty()) {
            optionalIamRole = iamLifecycle.getIamRole(tesRoleNameName);
            if (!optionalIamRole.isPresent()) {
                String errorString = String.format("Iam role name %s, passed as configuration, does not exist",
                        tesRoleNameName);
                throw new IllegalArgumentException(errorString);
            }
        }
        String csrPath = parameterValues.getString(FeatureParameters.CSR_PATH).orElse("");
        IotThingSpec thingSpec = getThingSpec(csrPath, thingGroupName, optionalIamRole);
        waitForRoleAliasUsable(thingSpec, resources.lifecycle(IotLifecycle.class).credentialsEndpoint());
        setupConfigWithConfigFile(configFile, thingSpec);
    }

    private void waitForRoleAliasUsable(IotThingSpec thingSpec, String credentialEndpoint)
            throws InterruptedException, IOException {
        if (thingSpec.roleAliasSpec() == null) {
            LOGGER.error("Cannot wait for role alias, spec was null");
            return;
        }
        String ra = thingSpec.roleAliasSpec().resource().roleAlias();
        IotCertificate certRes = thingSpec.resource().certificate();
        if (certRes == null) {
            LOGGER.error("Cannot wait for role alias, certificate was null");
            return;
        }

        try (SdkHttpClient client = ApacheHttpClient.builder().tlsKeyManagersProvider(() -> {
            try {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(null);

                List<X509Certificate> certificateChain = loadX509Certificates(certRes.certificatePem());
                keyStore.setKeyEntry("private-key", loadPrivateKeyPair(certRes.keyPair().privateKey()).getPrivate(),
                        null, certificateChain.toArray(new Certificate[0]));

                KeyManagerFactory keyManagerFactory =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, null);
                return keyManagerFactory.getKeyManagers();
            } catch (Exception e) {
                LOGGER.error("Failed to load key", e);
                throw new RuntimeException(e);
            }
        }).build()) {
            for (int i = 0; i < 20; i++) {
                HttpExecuteResponse res = client.prepareRequest(HttpExecuteRequest.builder().request(
                        SdkHttpRequest.builder().appendHeader("x-amzn-iot-thingname", thingSpec.thingName())
                                .method(SdkHttpMethod.GET).uri(URI.create(
                                        String.format("https://%s/role-aliases/%s/credentials", credentialEndpoint, ra)))
                                .build()).build()).call();
                if (res.httpResponse().statusCode() == 200) {
                    LOGGER.info("IoT Role alias returned 200, credentials should be good to go!");
                    return;
                } else {
                    LOGGER.info("IoT Role alias not ready yet, got {}: {}", res.httpResponse().statusCode(),
                            res.responseBody().isPresent() ? IoUtils.toUtf8String(res.responseBody().get()) : null);
                }
                Thread.sleep(6000);
            }
            throw new RuntimeException("Role alias never returned credentials, fail!");
        }
    }

    @VisibleForTesting
    void setupConfigWithConfigFile(String configFile, IotThingSpec thingSpec) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(configFile)) {
            setupConfig(
                    thingSpec.resource(),
                    thingSpec.roleAliasSpec(),
                    IoUtils.toUtf8String(input),
                    new HashMap<>());
        }
    }

    @VisibleForTesting
    IotThingSpec getThingSpec(String csrPath, String thingGroupName,
                              Optional<IamRole> optionalIamRole) throws IOException {
        // TODO: move this into iot steps.
        resources.create(GreengrassCoreDeviceSpec.builder().thingName(testContext.coreThingName()).build());
        return resources.create(IotThingSpec.builder()
                .thingName(testContext.coreThingName())
                .addThingGroups(IotThingGroupSpec.of(thingGroupName))
                // Currently in case of hsm certificate is expected to be already created and in hsm.
                .createCertificate(!testContext.hsmConfigured())
                .certificateSpec(IotCertificateSpec.builder()
                        .thingName(testContext.coreThingName())
                        .csr(!csrPath.isEmpty() ? Files.readAllBytes(Paths.get(csrPath)).toString() : "")
                        .existingArn(parameterValues.getString(FeatureParameters.EXISTING_DEVICE_CERTIFICATE_ARN)
                                .orElse(""))
                        .build())
                .policySpec(resources.trackingSpecs(IotPolicySpec.class)
                        .filter(p -> p.policyName().equals(testContext.testId().idFor("ggc-iot-policy")))
                        .findFirst()
                        .orElseGet(iotSteps::createDefaultPolicy))
                .roleAliasSpec(IotRoleAliasSpec.builder()
                        .name(testContext.testId().idFor("ggc-role-alias"))
                        .iamRole(optionalIamRole.orElseGet(() ->
                                resources.trackingSpecs(IamRoleSpec.class)
                                        .filter(s -> s.roleName().equals(testContext.testId().idFor("ggc-role")))
                                        .findFirst()
                                        .orElseGet(iamSteps::createDefaultIamRole)
                                        .resource()))
                        .build())
                .build());
    }

    private String getDefaultConfigName() {
        if (testContext.hsmConfigured()) {
            return DEFAULT_HSM_CONFIG;
        }
        return DEFAULT_CONFIG;
    }

    @VisibleForTesting
    void checkHSMConfigForPreInstalled() {
        Path configPath = testContext.installRoot().resolve("config")
                .resolve("effectiveConfig.yaml");
        byte[] bytes = platform.files().readBytes(configPath);
        try {
            JsonNode config = mapper.readTree(bytes);
            if (!config.get("services").hasNonNull("aws.greengrass.crypto.Pkcs11Provider")) {
                throw new IOException("Pkcs11 is not properly configured on device");
            }
        } catch (IOException e) {
            throw new ModuleProvisionException(e);
        }
    }

    @VisibleForTesting
    void setupConfig(
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
            if (!testContext.hsmConfigured()) {
                Files.write(testContext.testDirectory().resolve("privKey.key"), thing.certificate().keyPair()
                        .privateKey().getBytes(StandardCharsets.UTF_8));
                Files.write(testContext.testDirectory().resolve("thingCert.crt"), thing.certificate()
                        .certificatePem().getBytes(StandardCharsets.UTF_8));
            }
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

        if (testContext.hsmConfigured()) {
            config = config.replace("{ggc_hsm_slotLabel}", parameterValues.getString(HsmParameters.SLOT_LABEL)
                    .get());
            String pkcsLibPath = parameterValues.getString(HsmParameters.PKCS_LIBRARY_PATH).get();
            config = config.replace("{ggc_hsm_pkcs11ProviderPath}", pkcsLibPath);
            config = config.replace("{ggc_hsm_slotId}", parameterValues.getString(HsmParameters.SLOT_ID).get());
            config = config.replace("{ggc_hsm_slotUserPin}",
                    parameterValues.getString(HsmParameters.SLOT_USER_PIN).get());
            config = config.replace("{ggc.hsm.certandkey.label}", parameterValues
                    .getString(HsmParameters.HSM_CERT_AND_KEY_LABEL).orElse("greengrass-core"));
        }

        config = config.replace("{proxy_url}",
                resourcesContext.proxyConfig().map(ProxyConfig::proxyUrl).orElse(""));
        config = config.replace("{aws_region}", resourcesContext.region().metadata().id());
        config = config.replace("{nucleus_version}", testContext.coreVersion());
        config = config.replace("{env_stage}", resourcesContext.envStage());
        config = config.replace("{posix_user}", testContext.currentUser());
        config = config.replace("{data_plane_port}", Integer.toString(registrationContext.connectionPort()));
        if (parameterValues.get(ROOT_CA_PATH).isPresent()) {
            byte[] customRootCa = Files.readAllBytes(Paths.get(parameterValues.getString(ROOT_CA_PATH).get()));
            Files.write(testContext.testDirectory().resolve("rootCA.pem"), customRootCa);
        } else {
            Files.write(testContext.testDirectory().resolve("rootCA.pem"),
                    registrationContext.rootCA().getBytes(StandardCharsets.UTF_8));
        }

        Files.write(configFilePath.resolve("config.yaml"), config.getBytes(StandardCharsets.UTF_8));
        // Copy to where the nucleus will read it
        platform.files().makeDirectories(testContext.installRoot().getParent());
        platform.files().copyTo(testContext.testDirectory(), testContext.installRoot());
    }

}
