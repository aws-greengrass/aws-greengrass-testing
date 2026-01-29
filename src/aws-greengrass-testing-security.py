from typing import Generator, List, Tuple
from GGTestUtils import sleep_with_log
from pytest import fixture, mark
from src.IoTUtils import IoTUtils
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface

import time
import src.GGLSetup as ggl_setup


@fixture(scope="function")
def gg_util_obj(request) -> Generator[GGTestUtils, None, None]:
    aws_account = request.config.getoption("--aws-account")
    s3_bucket = request.config.getoption("--s3-bucket")
    region = request.config.getoption("--region")
    ggl_cli_path = request.config.getoption("--ggl-cli-path")

    gg_util_obj = GGTestUtils(aws_account, s3_bucket, region, ggl_cli_path)

    yield gg_util_obj

    gg_util_obj.cleanup()


@fixture(scope="function")
def iot_obj(request) -> Generator[IoTUtils, None, None]:
    region = request.config.getoption("--region")
    commit_id = request.config.getoption("--commit-id")
    iot_obj = IoTUtils(region)

    iot_obj.set_up_core_device()
    ggl_setup.setup_greengrass_lite(commit_id, region)

    yield iot_obj

    ggl_setup.clean_up()
    iot_obj.clean_up()


@fixture(scope="function")    # Runs for each test function
def system_interface() -> Generator[SystemInterface, None, None]:
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


ACL_TEST_TOPICS: List[Tuple[str, str, bool]] = [
    (r"test/topic", r"test/topic", True),
    (r"testStarWildcard/topic*suffix/match*",
     r"testStarWildcard/topic-A/B/C-suffix/match-star/1/2", True),
    # These escape sequences look to be not supported by lite
    # (r"test${?}escape${$}char*", r"test?escape$char1/2/3", True),
    (r"wrong/topic", r"test/topic", False),
    (r"testStarWildcard/topic*suffix/no-mat:ch*",
     r"testStarWildcard/topic-A/B/C-suffix/match-star/1/2", False),
    # (r"test${?}escape\${$}char", r"test${?}escape${$}char", False)
]

MQTT_TEST_TOPICS: List[Tuple[str, str, bool]] = [
    (r"topic-A/B-suffix/+/level/#",
     r"topic-A/B-suffix/matchPlus/level/match/pound", True),
    (r"topic-A/B-suffix/+/level/#",
     r"topic-A/B-suffix/matchPlus/extraLevel/level/match/pound", False)
    # Lite doesn't support mixing wildcards from both ACL and MQTT topic syntax.
    # (r"topic*suffix/+/level/#", r"topic-A/B-suffix/matchPlus/level/match/pound",
    #  True),
    # (r"topic*suffix/+/level/#",
    #  r"topic-A/B-suffix/matchPlus/extraLevel/level/match/pound", False)
]


# Scenario: Security-6-T2 & Security-6-T3 & Security-6-T4 & Security-6-T5
# As a service owner, I want to specify which components can and cannot publish and subscribe on which topic.
# @mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS)
@mark.skip(reason="TODO: Debug the case when the deployment should fail")
def test_Security_6_T2_T3_T4_T5_T10(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                                    system_interface: SystemInterface):
    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])
    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    payload = f"Test Component {pubsub_cloud_name}"

    pubsub_cloud_name = pubsub_cloud_name._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.pubsub": {
                    "HelloWorldPubSub:pubsub:1": {
                        "policyDescription":
                        "access to publish and subscribe to local topics",
                        "operations": [
                            "aws.greengrass#PublishToTopic",
                            "aws.greengrass#SubscribeToTopic"
                        ],
                        "resources": [resource]
                    }
                }
            },
            "Topic": topic,
            "Message": payload
        })

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id)
    if accepted:
        assert (deployment_result == 'SUCCEEDED')
    else:
        assert (deployment_result == 'FAILED')


# Scenario: Security-6-T2-mqtt & Security-6-T3-mqtt & Security-6-T4-mqtt & Security-6-T5-mqtt
# As a service owner, I want to specify which components can or cannot publish and subscribe on which mqtt topic
# @mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS + MQTT_TEST_TOPICS)
@mark.skip(reason="TODO: Debug the case when the deployment should fail")
def test_Security_6_T2_T3_T4_T5_mqtt(iot_obj: IoTUtils,
                                     gg_util_obj: GGTestUtils,
                                     system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    mqtt_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldMqtt", ["1.0.0"])
    if mqtt_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldMqtt cannot be uploaded to cloud")

    payload = f"\"Test Component {mqtt_cloud_name}\""

    mqtt_cloud_name = mqtt_cloud_name._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.mqttproxy": {
                    "HelloWorldMqtt:mqttproxy:1": {
                        "policyDescription":
                        "access to publish and subscribe to mqtt topics",
                        "operations": [
                            "aws.greengrass#PublishToIoTCore",
                            "aws.greengrass#SubscribeToIoTCore"
                        ],
                        "resources": [resource]
                    }
                }
            },
            "Topic": topic,
            "QOS": "1",
            "Message": payload
        })

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[mqtt_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id)
    if accepted:
        assert (deployment_result == 'SUCCEEDED')
    else:
        assert (deployment_result == 'FAILED')


# Scenario: Security-6-T6
# As a service owner, I want to specify that all components can publish and subscribe on all topics
def test_Security_6_T6(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                       system_interface: SystemInterface):
    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        240, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # And I get 1 assertion with context "Successfully subscribed to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully subscribed to test/topic",
        timeout=60) is True)

    #And I get 1 assertion with context "Successfully published to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully published to test/topic",
        timeout=60) is True)


# Scenario: Security-6-T7
# As a service owner, I want to ensure authorization persists across fresh restarts
def test_Security_6_T7(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                       system_interface: SystemInterface):
    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        240, deployment_id) == "SUCCEEDED")

    # And I get 1 assertion with context "Successfully subscribed to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully subscribed to test/topic",
        timeout=20) is True)

    #And I get 1 assertion with context "Successfully published to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully published to test/topic",
        timeout=20) is True)

    sleep_with_log(5)

    # When I restart the kernel
    assert (system_interface.restart_systemd_nucleus_lite(30) is True)

    # Then I can check the cli to see the status of component HelloWorldPubSub is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        pubsub_cloud_name[0]) == "RUNNING")

    sleep_with_log(5)

    # And I get 1 assertion with context "Successfully subscribed to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully subscribed to test/topic",
        timeout=20) is True)

    # And I get 1 assertion with context "Successfully published to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully published to test/topic",
        timeout=20) is True)


# Scenario: Security-6-T15: As a service owner, when I remove a component, all of that component's ACLs are removed as well
def test_Security_6_T15(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                        system_interface: SystemInterface):
    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    hello_world_pubSub = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["0.0.0"])
    if hello_world_pubSub is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    hello_world_pubSub = hello_world_pubSub._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.pubsub": {
                    "HelloWorldMqtt:pubsub:1": {
                        "policyDescription":
                        "access to publish to mqtt topics",
                        "operations": [
                            "aws.greengrass#PublishToTopic",
                            "aws.greengrass#SubscribeToTopic"
                        ],
                        "resources": ["test/topic"]
                    }
                }
            },
            "Topic": "test/topic",
            "QOS": "1",
            "Message": "Hello from local pubsub topic"
        })

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[hello_world_pubSub],
        deployment_name="FirstDeployment")["deploymentId"]

    # Then I can check the cli to see the status of component HelloWorldPubSub is RUNNING
    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        200, deployment_id)
    print(f"The deployment ({deployment_id}): {deployment_result}")
    assert (deployment_result == 'SUCCEEDED')

    sleep_with_log(20, "waiting for component to start and publish messages")

    # And I get 1 assertion with context "Successfully subscribed to test/topic"
    # And I get 1 assertion with context "Successfully published to test/topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + hello_world_pubSub[0] + ".service",
        "Successfully published 1 message(s)",
        timeout=30) is True)
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + hello_world_pubSub[0] + ".service",
        "Received new message on topic test/topic: Hello from local pubsub topic",
        timeout=30) is True)

    # When I remove the components HelloWorldPubSub
    # Then I can check the cli to see the component HelloWorldPubSub is not listed
    status = gg_util_obj.remove_component(
        deployment_id, hello_world_pubSub.name,
        gg_util_obj.get_thing_group_arn(security_thing_group_name))

    assert (status == 'SUCCEEDED')

    #This version of HelloWorldPubSub has no ACL
    #And I install the component HelloWorldPubSub version 0.0.1 from local store
    hello_world_pubSub = hello_world_pubSub._replace(
        merge_config={
            "Topic": "test/topic",
            "QOS": "1",
            "Message": "Hello from local pubsub topic"
        })
    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[hello_world_pubSub],
        deployment_name="FirstDeployment")["deploymentId"]
    # Then I can check the cli to see the status of component HelloWorldPubSub is RUNNING
    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id)
    print(f"The deployment ({deployment_id}): {deployment_result}")
    assert (deployment_result == 'FAILED')

    sleep_with_log(5)

    # And I get 1 assertion with context "IPC error"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + hello_world_pubSub[0] + ".service", "IPC error", timeout=20)
            is True)


# Scenario: Security-6-T22
# As a service owner, if I have multiple ACL policies, I can update one at a time
def test_Security_6_T22(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                        system_interface: SystemInterface):

    security_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    security_thing_group_name = iot_obj.generate_thing_group_name(id)
    security_thing_group_result = iot_obj.add_thing_to_thing_group(
        security_thing_name, security_thing_group_name)
    assert security_thing_group_result is True

    # When I install the component PubsubSubscriber version 0.0.0 from local store
    subscriber_cloud_name = gg_util_obj.upload_component_with_versions(
        "PubsubSubscriber", ["0.0.0"])
    assert subscriber_cloud_name is not None

    # And I install the component PubsubPublisher version 0.0.0 from local store
    publisher_cloud_name = gg_util_obj.upload_component_with_version_and_deps(
        "PubsubPublisher", "0.0.0",
        [("PubsubSubscriber", subscriber_cloud_name.name)])
    assert publisher_cloud_name is not None

    deployment_1 = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[subscriber_cloud_name, publisher_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_1) == "SUCCEEDED")

    sleep_with_log(5)

    # And I get 1 assertion with context "Subscribed to pubsub topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + subscriber_cloud_name[0] + ".service",
        "Subscribed to pubsub topic",
        timeout=20) is True)

    # And I get 1 assertion with context "Published to pubsub topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + publisher_cloud_name[0] + ".service",
        "Published to pubsub topic",
        timeout=20) is True)

    # And I get 1 assertion with context "Received new message: Hello world"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + subscriber_cloud_name[0] + ".service",
        "Received new message: Hello world",
        timeout=20) is True)

    sleep_with_log(5)

    # And I install the component PubsubPublisher version 0.0.0 from local store with replaced configuration and restart
    publisher_cloud_name = publisher_cloud_name._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.pubsub": {
                    "policyId2": {
                        "policyDescription": "access to pubsub topics",
                        "operations": ["aws.greengrass#SubscribeToTopic"],
                        "resources": ["*"]
                    }
                }
            }
        })

    deployment_2 = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group_name),
        component_list=[subscriber_cloud_name, publisher_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_2) == "FAILED")

    sleep_with_log(5)

    # And I get 1 assertion with context "Subscribed to pubsub topic"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + subscriber_cloud_name[0] + ".service",
        "Subscribed to pubsub topic",
        timeout=20) is True)

    # And I get 1 assertion with context "UnauthorizedError"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + publisher_cloud_name[0] + ".service",
        "UnauthorizedError",
        timeout=20) is True)
