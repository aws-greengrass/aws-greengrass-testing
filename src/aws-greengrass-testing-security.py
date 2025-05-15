import time
from typing import Generator, List, Tuple
from pytest import fixture, mark
from src.GGTestUtils import GGTestUtils
from src.IoTUtils import IoTTestUtils
from src.SystemInterface import SystemInterface
from config import config


@fixture(scope="function")    # Runs for each test function
def gg_util_obj() -> Generator[GGTestUtils, None, None]:
    # Setup an instance of the GGUtils class. It is then passed to the
    # test functions.
    gg_util = GGTestUtils(config.aws_account, config.s3_bucket_name,
                          config.region, config.ggl_cli_bin_path,
                          config.ggl_install_dir)

    # yield the instance of the class to the tests.
    yield gg_util

    # This section is called AFTER the test is run.

    # Cleanup the artifacts, components etc.
    gg_util.cleanup()


@fixture(scope="function")    # Runs for each test function
def iot_obj() -> Generator[IoTTestUtils, None, None]:
    # Setup an IoT object. It is then passed to the
    # test functions.
    iot_obj = IoTTestUtils(
        config.aws_account,
        config.region,
    )

    # yield the instance of the class to the tests.
    yield iot_obj

    # This section is called AFTER the test is run.

    # Cleanup the artifacts, components etc.
    iot_obj.cleanup()


@fixture(scope="function")    # Runs for each test function
def system_interface() -> Generator[SystemInterface, None, None]:
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This secion is called AFTER the test is run.
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
@mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS)
def test_Security_6_T2_T3_T4_T5(gg_util_obj: GGTestUtils, iot_obj: IoTTestUtils,
                                resource: str, topic: str, accepted: bool):
    # Get an auto generated thing group to which the thing is added.
    new_thing_group = iot_obj.add_thing_to_thing_group(config.thing_name,
                                                       "NewThingGroup")
    assert new_thing_group is not None

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
        thingArn=gg_util_obj.get_thing_group_arn(new_thing_group),
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
@mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS + MQTT_TEST_TOPICS)
def test_Security_6_T2_T3_T4_T5_mqtt(gg_util_obj: GGTestUtils,
                                     iot_obj: IoTTestUtils, resource: str,
                                     topic: str, accepted: bool):
    # Get an auto generated thing group to which the thing is added.
    new_thing_group = iot_obj.add_thing_to_thing_group(config.thing_name,
                                                       "NewThingGroup")
    assert new_thing_group is not None

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
        thingArn=gg_util_obj.get_thing_group_arn(new_thing_group),
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
def test_Security_6_T6(gg_util_obj: GGTestUtils, iot_obj: IoTTestUtils,
                       system_interface: SystemInterface):
    security_thing_group = iot_obj.add_thing_to_thing_group(
        config.thing_name, "SecurityThingGroup")
    assert security_thing_group is not None

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

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


# Scenario: Security-6-T7
# As a service owner, I want to ensure authorization persists across fresh restarts
def test_Security_6_T7(gg_util_obj: GGTestUtils, iot_obj: IoTTestUtils,
                       system_interface: SystemInterface):
    security_thing_group = iot_obj.add_thing_to_thing_group(
        config.thing_name, "SecurityThingGroup")
    assert security_thing_group is not None

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(security_thing_group),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

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

    time.sleep(5)

    # When I restart the kernel
    assert (system_interface.restart_systemd_nucleus_lite(30) is True)

    # Then I can check the cli to see the status of component HelloWorldPubSub is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        pubsub_cloud_name[0]) == "RUNNING")

    time.sleep(5)

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
