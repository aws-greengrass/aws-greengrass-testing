import time
import pytest
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface
from config import config


@pytest.fixture(scope="function")    # Runs for each test function
def gg_util_obj():
    # Setup an instance of the GGUtils class. It is then passed to the
    # test functions.
    gg_util = GGTestUtils(config.aws_account, config.s3_bucket_name,
                          config.region, config.ggl_cli_bin_path)

    # yield the instance of the class to the tests.
    yield gg_util

    # This section is called AFTER the test is run.

    # Cleanup the artifacts, components etc.
    gg_util.cleanup()


@pytest.fixture(scope="function")    # Runs for each test function
def system_interface():
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


# Scenario: FleetStatus-1-T1: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment succeeds
def test_FleetStatus_1_T1(gg_util_obj: GGTestUtils):
    # When I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [component_cloud_name],
        "FirstDeployment",
    )["deploymentId"]    #TODO: create and use FssThingGroup instead of thing_group_1
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I can get the thing status as "HEALTHY" with all uploaded components within 60 seconds with groups
    #      | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(
        60, config.thing_group_1, "HEALTHY"))


#Scenario: FleetStatus-1-T3: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment fails
def test_FleetStatus_1_T3(gg_util_obj):
    # When I upload component BrokenAfterDeployed version 1.0.0 with configuration from the local store
    #         | key               | value |
    #         | sleepValueSeconds | 10    |  #TODO: Allow configurable value
    # Then I ensure component "BrokenAfterDeployed" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenAfterDeployed", "1.0.0")
    time.sleep(10)

    #    And I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | BrokenAfterDeployed | 1.0.0 |
    #    And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_cloud_name], "FirstDeployment"
    )["deploymentId"]    #TODO: create and use FssThingGroup instead of thing_group_1

    #    Then the deployment FirstDeployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    #    And I can get the thing status as "UNHEALTHY" with all uploaded components within 180 seconds with groups
    #        | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(180, config.thing_group_1,
                                                  "UNHEALTHY")
            )    #TODO: create and use FssThingGroup instead of thing_group_1
