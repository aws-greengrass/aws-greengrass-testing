package com.aws.greengrass.testing.features.mqtt;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ScenarioScoped
public class MQTTSteps {
    private static final Logger LOGGER = LogManager.getLogger(MQTTSteps.class);
    private final TestContext testContext;
    private final ScenarioContext scenarioContext;
    private final AWSResources resources;
    private final IotSteps iotSteps;
    private final RegistrationContext registrationContext;
    private final ClientBootstrap bootstrap;
    private final Map<String, List<MqttMessage>> mqttResponses;
    private MqttClientConnection connection;

    @Inject
    public MQTTSteps(
            final AWSResources resources,
            final TestContext testContext,
            final ScenarioContext scenarioContext,
            final RegistrationContext registrationContext,
            final ClientBootstrap bootstrap,
            final IotSteps iotSteps) {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.bootstrap = bootstrap;
        this.resources = resources;
        this.iotSteps = iotSteps;
        mqttResponses = new ConcurrentHashMap<>();
    }

    @Given("I connect an MQTT client to IoT")
    public void connect() throws ExecutionException, InterruptedException {
        if (Objects.isNull(connection)) {
            IotLifecycle lifecycle = resources.lifecycle(IotLifecycle.class);
            IotThingSpec hostThing = resources.create(IotThingSpec.builder()
                    .createCertificate(true)
                    .thingName(testContext.testId().idFor("host-mqtt"))
                    .policySpec(iotSteps.createDefaultPolicy("host-mqtt-policy"))
                    .build());

            connection = AwsIotMqttConnectionBuilder.newMtlsBuilder(
                    hostThing.resource().certificate().certificatePem(),
                    hostThing.resource().certificate().keyPair().privateKey())
                    .withBootstrap(bootstrap)
                    .withClientId(hostThing.thingName())
                    .withCertificateAuthority(registrationContext.rootCA())
                    .withPort((short) registrationContext.connectionPort())
                    .withCleanSession(true)
                    .withEndpoint(lifecycle.dataEndpoint())
                    .build();

            assertTrue(connection.connect().get(), "Could not connect to IoT core!");
        }
    }

    public boolean isConnected() {
        return Objects.nonNull(connection);
    }

    @Given("I subscribe to the following IoT MQTT topics")
    public void subcribeToTopics(final List<String> topics) throws ExecutionException, InterruptedException {
        for (String topic : topics) {
            String realTopic = new StringJoiner("/")
                    .add(testContext.testId().id())
                    .add(topic)
                    .toString();
            scenarioContext.put(topic, realTopic);
            CompletableFuture<Integer> subscription = connection.subscribe(realTopic, QualityOfService.AT_LEAST_ONCE, message -> {
                mqttResponses.compute(message.getTopic(), (key, list) -> {
                    List<MqttMessage> ls = Optional.ofNullable(list).orElseGet(ArrayList::new);
                    ls.add(message);
                    return ls;
                });
            });
            int errorCode = subscription.get();
            assertEquals(0, errorCode, CRT.awsErrorString(errorCode));
        }
    }

    @Then("I ")

    @After(order = 997899)
    public void disconnect() throws ExecutionException, InterruptedException {
        if (Objects.nonNull(connection)) {
            connection.disconnect().get();
        }
    }
}
