from typing import List, Tuple
from pytest import fixture, mark
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface
from config import config


@fixture(scope="function")    # Runs for each test function
def gg_util_obj():
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
def system_interface():
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



# Scenario: Security-6-T2 & Security-6-T3
# As a service owner, I want to specify which components can and cannot publish on which topic.
@mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS)
def test_Security_T2_Security_T3(gg_util_obj: GGTestUtils,
                                 system_interface: SystemInterface,
                                 resource: str, topic: str, accepted: bool):
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])
    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: IotMqttPublisher_cloud_name cannot be uploaded to cloud"
        )

    payload = f"Test Component {pubsub_cloud_name}"

    pubsub_cloud_name = pubsub_cloud_name._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.pubsub": {
                    "HelloWorldPubSub:pubsub:1": {
                        "policyDescription":
                        "access to publish to local topics",
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
        thingArn=gg_util_obj.get_thing_group_arn(config.thing_group_1),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id)
    if accepted:
        assert (deployment_result == 'SUCCEEDED')
    else:
        assert (deployment_result == 'FAILED')


@mark.parametrize("resource,topic,accepted", ACL_TEST_TOPICS + MQTT_TEST_TOPICS)
def test_Security_6_T2_mqtt_Security_6_T3_mqtt(
        gg_util_obj: GGTestUtils, system_interface: SystemInterface,
        resource: str, topic: str, accepted: bool):
    mqtt_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldMqtt", ["1.0.0"])
    if mqtt_cloud_name is None:
        raise RuntimeError(
            "Fatal error: IotMqttPublisher_cloud_name cannot be uploaded to cloud"
        )

    payload = f"\"Test Component {mqtt_cloud_name}\""

    mqtt_cloud_name = mqtt_cloud_name._replace(
        merge_config={
            "accessControl": {
                "aws.greengrass.ipc.mqttproxy": {
                    "HelloWorldMqtt:mqttproxy:1": {
                        "policyDescription":
                        "access to publish to mqtt topics",
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
        thingArn=gg_util_obj.get_thing_group_arn(config.thing_group_1),
        component_list=[mqtt_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    deployment_result = gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id)
    if accepted:
        assert (deployment_result == 'SUCCEEDED')
    else:
        assert (deployment_result == 'FAILED')
