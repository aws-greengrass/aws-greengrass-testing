import json
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


#TODO: INCOMPLETE only partially implemented
# Scenario Outline: Security-6-T2-mqtt: As a service owner, I want to specify which components can publish on which mqtt topic
def test_Security_6_T2_mqtt(gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    #   When I install the component IotMqttPublisher version 0.0.0 from local store with configuration
    #     | value                                                                                                                                                                                                                                                        |
    #     | {"MERGE":{"accessControl":{"aws.greengrass.ipc.mqttproxy":{"policyId1":{"policyDescription":"access to publish to mqtt topics","operations":["aws.greengrass#PublishToIoTCore"],"resources":["<resource>"]}}},"topic":"<topic>","QOS":"1","payload":"test"}} |
    IotMqttPublisher_cloud_name = gg_util_obj.upload_component_with_versions(
        "IotMqttPublisher", ["0.0.0"])
    if IotMqttPublisher_cloud_name is None:
        raise RuntimeError(
            "Fatal error: IotMqttPublisher_cloud_name cannot be uploaded to cloud"
        )

    #TODO: MOdify this to use the python pubsub assertion component.
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1), [
            IotMqttPublisher_cloud_name +
            ("{\"accessControl\":{\"aws.greengrass.ipc.mqttproxy\":{\"policyId1\":{\"policyDescription\":\"access to publish to mqtt topics\",\"operations\":[\"aws.greengrass#PublishToIoTCore\"],\"resources\":[\"<resource>\"]}}},\"topic\":\"<topic>\",\"QOS\":\"1\",\"payload\":\"test\"}",
             )
        ], "FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    #   Then I can check the cli to see the status of component IotMqttPublisher is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        IotMqttPublisher_cloud_name[0]) == "RUNNING"


#   And I get 1 assertion with context "Successfully published to IoT topic <topic>"
#   Examples:
#     | resource                             | topic                                              |
#     | test/topic                           | test/topic                                         |
#     | testStarWildcard/topic*suffix/match* | testStarWildcard/topic-A/B/C-suffix/match-star/1/2 |
#     | topic*suffix/+/level/#               | topic-A/B-suffix/matchPlus/level/match/pound       |
#     | test\${?}escape\${$}char*            | test?escape$char1/2/3                              |


# Scenario: Security-6-T2 & Security-6-T3
# As a service owner, I want to specify which components can and cannot publish on which topic.
@mark.parametrize(
    "resource,topic,accepted",
    [
        (r"test/topic", r"test/topic", True),
        (r"testStarWildcard/topic*suffix/match*",
         r"testStarWildcard/topic-A/B/C-suffix/match-star/1/2", True),
    # Not supported by lite
    # ( r"test${?}escape${$}char*"               , r"test?escape$char1/2/3"                              , True  ),
        (r"wrong/topic", r"test/topic", False),
        (r"testStarWildcard/topic*suffix/no-match*",
         r"testStarWildcard/topic-A/B/C-suffix/match-star/1/2", False),
    # ( r"test${?}escape\${$}char"                , r"test${?}escape${$}char"                           , False )
    ])
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
                    "policyId1": {
                        "policyDescription":
                        "access to publish to mqtt topics",
                        "operations": [
                            "aws.greengrass#PublishToTopic",
                            "aws.greengrass#SubscribeToTopic"
                        ],
                        "resources": [resource]
                    }
                }
            },
            "Topic": topic,
            "QOS": "1",
            "payload": payload
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
