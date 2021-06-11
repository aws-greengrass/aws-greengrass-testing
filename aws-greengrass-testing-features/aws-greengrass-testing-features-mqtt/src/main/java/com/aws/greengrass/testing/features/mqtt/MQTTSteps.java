package com.aws.greengrass.testing.features.mqtt;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.features.WaitSteps;
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
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@ScenarioScoped
public class MQTTSteps {
    private static final Logger LOGGER = LogManager.getLogger(MQTTSteps.class);
    private final TestContext testContext;
    private final ScenarioContext scenarioContext;
    private final AWSResources resources;
    private final IotSteps iotSteps;
    private final RegistrationContext registrationContext;
    private final WaitSteps waits;
    private final Map<String, List<MqttMessage>> mqttResponses;
    private MqttClientConnection connection;

    @Inject
    public MQTTSteps(
            final AWSResources resources,
            final TestContext testContext,
            final ScenarioContext scenarioContext,
            final RegistrationContext registrationContext,
            final WaitSteps waits,
            final IotSteps iotSteps) {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.resources = resources;
        this.iotSteps = iotSteps;
        this.waits = waits;
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
            try (EventLoopGroup loopGroup = new EventLoopGroup(1);
                 HostResolver resolver = new HostResolver(loopGroup);
                ClientBootstrap bootstrap = new ClientBootstrap(loopGroup, resolver);
                AwsIotMqttConnectionBuilder builder = AwsIotMqttConnectionBuilder.newMtlsBuilder(
                        hostThing.resource().certificate().certificatePem(),
                        hostThing.resource().certificate().keyPair().privateKey())) {

                connection = builder
                        .withBootstrap(bootstrap)
                        .withKeepAliveMs(31_000)
                        .withPingTimeoutMs(30_000)
                        .withClientId(hostThing.thingName())
                        .withCertificateAuthority(registrationContext.rootCA())
                        .withCleanSession(false)
                        .withEndpoint(lifecycle.dataEndpoint())
                        .build();

                connection.connect().get();
            }
        }
    }

    public boolean isConnected() {
        return Objects.nonNull(connection);
    }

    @Given("I subscribe to the following IoT MQTT topics")
    public void subcribeToTopics(final List<String> topics) throws ExecutionException, InterruptedException {
        if (!isConnected()) {
            connect();
        }
        for (String topic : topics) {
            String realTopic = new StringJoiner("/")
                    .add(testContext.testId().id())
                    .add(topic)
                    .toString();
            scenarioContext.put(topic, realTopic);
            CompletableFuture<Integer> subscription = connection.subscribe(realTopic, QualityOfService.AT_LEAST_ONCE, message -> {
                LOGGER.debug("Received MQTT message on {}", message.getTopic());
                mqttResponses.compute(topic, (key, list) -> {
                    List<MqttMessage> ls = Optional.ofNullable(list).orElseGet(ArrayList::new);
                    ls.add(message);
                    return ls;
                });
            });
            subscription.get();
        }
    }

    @When("I publish messages on the following IoT MQTT topics")
    public void publishMessages(List<List<String>> topicTuples) throws ExecutionException, InterruptedException {
        if (!isConnected()) {
            connect();
        }
        for (List<String> tuple : topicTuples) {
            if (tuple.size() < 2) {
                continue;
            }
            String realTopic = scenarioContext.get(tuple.get(0));
            if (Objects.isNull(realTopic)) {
                continue;
            }
            MqttMessage message = new MqttMessage(realTopic,
                    tuple.get(1).getBytes(StandardCharsets.UTF_8),
                    QualityOfService.AT_LEAST_ONCE);
            CompletableFuture<Integer> post = connection.publish(message);
            post.get();
        }
    }

    @Then("I receive messages on the following IoT MQTT topics after {int} {word}")
    public void checkReceived(int value, String unit, List<List<String>> topicTuples) throws InterruptedException {
        waits.untilTerminal(
                mqttResponses::keySet,
                topics -> topicTuples.stream().allMatch(this::testTopicPair),
                topics -> topics.containsAll(topicTuples.stream().map(ls -> ls.get(0)).collect(Collectors.toList())),
                value, TimeUnit.valueOf(unit.toUpperCase()));
    }

    private boolean testTopicPair(List<String> tuple) {
        if (!mqttResponses.containsKey(tuple.get(0))) {
            return false;
        }
        if (tuple.size() < 2) {
            return true;
        }
        return mqttResponses.get(tuple.get(0)).stream()
                .anyMatch(message -> new String(message.getPayload(), StandardCharsets.UTF_8).contains(tuple.get(1)));
    }

    @After(order = 997899)
    public void disconnect() throws ExecutionException, InterruptedException {
        if (Objects.nonNull(connection)) {
            connection.disconnect().get();
        }
    }
}
