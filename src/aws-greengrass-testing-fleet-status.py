from typing import Generator
from GGTestUtils import sleep_with_log
from pytest import fixture
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


# Scenario: FleetStatus-1-T1: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment succeeds
def test_FleetStatus_1_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                          system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    sleep_with_log(5)

    # When I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [component_cloud_name],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I can get the thing status as "HEALTHY" with all uploaded components within 60 seconds with groups
    #      | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))


# Scenario: FleetStatus-1-T3: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment fails
def test_FleetStatus_1_T3(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                          system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component BrokenAfterDeployed version 1.0.0 with configuration from the local store
    #         | key               | value |
    #         | sleepValueSeconds | 10    |  #TODO: Allow configurable value
    # Then I ensure component "BrokenAfterDeployed" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "BrokenAfterDeployed", ["1.0.0"])
    sleep_with_log(10)

    #    And I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | BrokenAfterDeployed | 1.0.0 |
    #    And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [broken_component_cloud_name], "FirstDeployment")["deploymentId"]

    #    Then the deployment FirstDeployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    #    And I can get the thing status as "UNHEALTHY" with all uploaded components within 180 seconds with groups
    #        | FssThingGroup |
    assert (gg_util_obj.wait_ggcore_device_status(180, fss_thing_group_name,
                                                  "UNHEALTHY"))


# Scenario: FleetStatus-2-T2: Event-based FSS keeps the device HEALTHY when
# a normal component reaches the RUNNING terminal state (no false UNHEALTHY
# triggered by the broadcast of a non-failure terminal state).
def test_FleetStatus_2_T2(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                          system_interface: SystemInterface):
    # Given a thing group with the device added
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component "HelloWorld" version "1.0.0" (runs healthy)
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])
    sleep_with_log(5)

    # And I create and deploy a deployment with HelloWorld
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [component_cloud_name],
        "EventFssHealthyDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment succeeds within 120s
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    # And the device reaches HEALTHY within 60s (component enters RUNNING)
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))

    # Wait 20s past the event-based forwarding window to confirm the
    # RUNNING terminal-state broadcast does NOT degrade the device status
    sleep_with_log(20, "waiting past event-based forwarding window")

    # Then the device is STILL HEALTHY — a RUNNING terminal-state
    # broadcast must not cause a false UNHEALTHY via the event path
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))


# Scenario: FleetStatus-1-T2: As a customer I can get thing information with
# status of a BROKEN component
# Derived from EvergreenFeatures fleet-status-1.feature
def test_FleetStatus_1_T2(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                          system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component "BrokenComponent" version "1.0.0"
    broken_cloud = gg_util_obj.upload_component_with_versions(
        "BrokenComponent", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    sleep_with_log(5)

    # When I create and deploy deployment FirstDeployment with BrokenComponent
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [broken_cloud],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And the device thing status is UNHEALTHY within 60 seconds
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "UNHEALTHY"))


# Scenario: FleetStatus-1-T5: Components which were removed after an IoT Jobs
# deployment succeeds
# Derived from EvergreenFeatures fleet-status-1.feature
def test_FleetStatus_1_T5(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                          system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component "HelloWorld" version "1.0.0"
    component_cloud = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    sleep_with_log(5)

    # When I create and deploy deployment FirstDeployment with HelloWorld
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [component_cloud],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And the device is HEALTHY within 60 seconds
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))

    # When I create an empty deployment removing all components
    removal_result = gg_util_obj.remove_all_components(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name))

    # Then the removal deployment completes with SUCCEEDED
    assert removal_result == "SUCCEEDED"

    # And the device is still HEALTHY within 60 seconds
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))


# Scenario: FleetStatus-UninstalledReporting-T1: When a deployment fully
# removes a component, fleet status reports it to the cloud as UNINSTALLED so
# the cloud prunes it from the device's installed-component inventory.
# Validates aws-greengrass-lite commit c50aec3b ("Add support for reporting
# stale components as UNINSTALLED in fleet status"). Derived from the
# component-removal flow in EvergreenFeatures fleet-status-1.feature (T5).
def test_FleetStatus_UninstalledReporting_T1(iot_obj: IoTUtils,
                                             gg_util_obj: GGTestUtils,
                                             system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    fss_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    fss_thing_group_name = iot_obj.generate_thing_group_name(id)
    fss_thing_group_result = iot_obj.add_thing_to_thing_group(
        fss_thing_name, fss_thing_group_name)
    assert fss_thing_group_result is True

    # When I upload component "HelloWorld" version "1.0.0"
    component_cloud = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    sleep_with_log(5)

    # And I deploy a deployment with HelloWorld to the thing group
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name),
        [component_cloud],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And the device is HEALTHY within 60 seconds
    assert (gg_util_obj.wait_ggcore_device_status(60, fss_thing_group_name,
                                                  "HEALTHY"))

    # And the cloud reports the component as installed within 60 seconds
    # (use the randomized cloud component name, not the local recipe name)
    assert (gg_util_obj.wait_for_cloud_component_installed(
        60, fss_thing_group_name, component_cloud.name))

    # When I create an empty deployment that removes all components
    removal_result = gg_util_obj.remove_all_components(
        gg_util_obj.get_thing_group_arn(fss_thing_group_name))

    # Then the removal deployment completes with SUCCEEDED
    assert removal_result == "SUCCEEDED"

    # And the cloud prunes the component from the device inventory within 90s
    # because fleet status reported it as UNINSTALLED on the removal update
    assert (gg_util_obj.wait_for_cloud_component_uninstalled(
        90, fss_thing_group_name, component_cloud.name))
