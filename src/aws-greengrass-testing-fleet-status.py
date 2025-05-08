import time
from typing import Generator
from pytest import fixture
from pytest import mark
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
def system_interface() -> Generator[SystemInterface, None, None]:
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


@fixture(scope="function")    # Runs for each test function
def iot_obj() -> Generator[IoTTestUtils, None, None]:
    # Setup an instance of the GGUtils class. It is then passed to the
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


# Scenario: FleetStatus-1-T1: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment succeeds
def test_FleetStatus_1_T1(gg_util_obj: GGTestUtils, iot_obj: IoTTestUtils):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_group = iot_obj.add_thing_to_thing_group(config.thing_name,
                                                       "FssThingGroup")
    assert fss_thing_group is not None

    # When I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group),
        [component_cloud_name],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I can get the thing status as "HEALTHY" with all uploaded components within 60 seconds with groups
    #      | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group,
                                                  "HEALTHY"))


#Scenario: FleetStatus-1-T3: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment fails
@mark.skip(reason="TODO: Fix fleet status to update on GC failure")
def test_FleetStatus_1_T3(gg_util_obj: GGTestUtils):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_group = iot_obj.add_thing_to_thing_group(config.thing_name,
                                                       "FssThingGroup")
    assert fss_thing_group is not None

    # When I upload component BrokenAfterDeployed version 1.0.0 with configuration from the local store
    #         | key               | value |
    #         | sleepValueSeconds | 10    |  #TODO: Allow configurable value
    # Then I ensure component "BrokenAfterDeployed" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "BrokenAfterDeployed", ["1.0.0"])
    time.sleep(10)

    #    And I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | BrokenAfterDeployed | 1.0.0 |
    #    And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group),
        [broken_component_cloud_name], "FirstDeployment")["deploymentId"]

    #    Then the deployment FirstDeployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    #    And I can get the thing status as "UNHEALTHY" with all uploaded components within 180 seconds with groups
    #        | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(180, fss_thing_group,
                                                  "UNHEALTHY"))
