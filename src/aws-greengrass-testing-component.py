from typing import Generator
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


# As a component developer, I can create Greengrass component that works on my current platform.
def test_Component_12_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # I upload component "MultiPlatform" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "MultiPlatform", ["1.0.0"])

    # And  I create a deployment configuration with components and configuration
    #   | MultiPlatform | 1.0.0 |
    # And   I deploy the deployment configuration

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # And  I can check the cli to see the status of component MultiPlatform is RUNNING
    """ GG LITE CLI DOES NOT SUPPORT THIS YET. """

    # And  the MultiPlatform log eventually contains the line "Hello world!" within 20 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "Hello world! World",
        timeout=20) is True)


# GC developer can create a component with recipes containing s3 artifact. GGC operator can deploy it and artifact can be run.
def test_Component_16_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # Then I can check the cli to see the status of component HelloWorld is RUNNING
    """ GG LITE CLI DOES NOT SUPPORT THIS YET. """

    # Then the HelloWorld log contains the line "Evergreen's dev experience is great!"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "Evergreen's dev experience is great!",
        timeout=20,
    ) is True)


# As a component developer, I expect kernel to fail the deployment if the checksum of downloaded artifacts does not match with the checksum in the recipe.
def test_Component_27_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):

    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # Given I upload component "HelloWorld" version "1.0.0" from the local store
    # And I ensure component "HelloWorld" version "1.0.0" exists on cloud within 120 seconds
    # And kernel registered as a Thing
    # And my device is running the evergreen-kernel
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorld", ["1.0.0"])

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    sleep_with_log(5)

    # When I corrupt the contents of the component HelloWorld version 1.0.0 in the S3 bucket
    assert gg_util_obj.upload_corrupt_artifacts_to_s3("HelloWorld",
                                                      "1.0.0") is True

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    # Greengrass retries 10 times with a 1 minute interval
    # Then the deployment completes with FAILED within 630 seconds
    assert gg_util_obj.wait_for_deployment_till_timeout(
        630, deployment_id) == "FAILED"

    # the greengrass log eventually contains the line "Failed to verify digest." within 30 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.core.ggdeploymentd.service",
        "Failed to verify digest.",
        timeout=30,
    ) is True)


# As an operator, I can interpolate component default configurations by local deployment.
@mark.skip("TODO: If a config value doesn't exist - interpolation should not happen which is not correct." \
"                   2. Quatation marks should be escaped when set as an environment variable.")
def test_Component_29_T0(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I install the component aws.gg.uat.local.ComponentConfigTestService version 1.0.0 from local store
    component_artifacts_dir = "./components/local_artifacts/"
    component_recipe_dir = "./components/aws.gg.uat.local.ComponentConfigTestService/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        component_artifacts_dir, component_recipe_dir,
        "aws.gg.uat.local.ComponentConfigTestService=1.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 120
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "aws.gg.uat.local.ComponentConfigTestService") == "FINISHED":
            break
        sleep_with_log(1)
        timeout -= 1

    # I can check the cli to see the status of component aws.gg.uat.local.ComponentConfigTestService is FINISHED
    assert (system_interface.check_systemctl_status_for_component(
        "aws.gg.uat.local.ComponentConfigTestService") == "FINISHED")

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /singleLevelKey: default value of singleLevelKey"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /singleLevelKey: default value of singleLevelKey",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /nestedKey/leafKey: default value of /nestedKey/leafKey."
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /nestedKey/leafKey: default value of /nestedKey/leafKey.",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /nestedKey: {"leafKey":"default value of /nestedKey/leafKey"}. I will be interpolated as a serialized JSON String."
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /nestedKey: {\"leafKey\":\"default value of /nestedKey/leafKey\"}. I will be interpolated as a serialized JSON String.",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /listKey/0: item1."
    # TODO: Add this after we support json pointer support for list indices. This logging has been removed from the component recipe for now.

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /emptyStringKey: ."
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /emptyStringKey: .",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /defaultIsNullKey: null"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /defaultIsNullKey: null",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Value for /newSingleLevelKey: {configuration:/newSingleLevelKey}."
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Value for /newSingleLevelKey: {configuration:/newSingleLevelKey}.",
        timeout=20) is True)

    # And the aws.gg.uat.local.ComponentConfigTestService log contains the line "Verified JSON interpolation from script"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.aws.gg.uat.local.ComponentConfigTestService.service",
        "Verified JSON interpolation from script",
        timeout=20) is True)

    # I can use greengrass-cli component details -n to check the component aws.gg.uat.local.ComponentConfigTestService has configuration that is equal to JSON:
    #     """
    #     {
    #       "defaultIsNullKey": null,
    #       "emptyListKey": [],
    #       "emptyObjectKey": {},
    #       "emptyStringKey": "",
    #       "listKey": [
    #         "item1",
    #         "item2"
    #       ],
    #       "nestedKey": {
    #         "leafKey": "default value of /nestedKey/leafKey"
    #       },
    #       "singleLevelKey": "default value of singleLevelKey",
    #       "willBeNullKey": "I will be set to null soon"
    #     }
    #     """
    # GG_LITE CLI doesn't support this yet.


# As an operator, I can update component configurations from multiple sources, by doing a mix of cloud and local deployments.
@mark.skip("TODO: If a config value doesn't exist - interpolation should not happen which is not correct." \
"                   2. Quatation marks should be escaped when set as an environment variable.")
def test_Component_29_T4(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):

    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # I upload component "aws.gg.uat.cloud.ComponentConfigTestService" version "1.0.0" from the local store
    # I ensure component "aws.gg.uat.cloud.ComponentConfigTestService" version "1.0.0" exists on cloud with scope private within 60 seconds
    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "aws.gg.uat.cloud.ComponentConfigTestService", ["1.0.0"])

    # I create a deployment configuration for deployment FirstCloudDeployment with components
    #         | aws.gg.uat.cloud.ComponentConfigTestService | 1.0.0 |
    # I deploy the configuration for deployment FirstCloudDeployment

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name], "FirstCloudDeployment")["deploymentId"]
    assert deployment_id is not None

    # the deployment FirstCloudDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # I can check the cli to see the status of component aws.gg.uat.cloud.ComponentConfigTestService is FINISHED
    assert (system_interface.check_systemctl_status_for_component(
        "aws.gg.uat.cloud.ComponentConfigTestService") == "FINISHED")

    # I can use greengrass-cli component details -n to check the component aws.gg.uat.cloud.ComponentConfigTestService has configuration that is equal to JSON:
    # """
    # {
    #   "emptyListKey": [],
    #   "emptyObjectKey": {},
    #   "emptyStringKey": "",
    #   "listKey": [
    #     "item1",
    #     "item2"
    #   ],
    #   "nestedKey": {
    #     "leafKey": "default value of /nestedKey/leafKey"
    #   },
    #   "singleLevelKey": "default value of singleLevelKey",
    #   "willBeNullKey": "I will be set to null soon",
    #   "defaultIsNullKey": null
    # }
    # """
    # GG_LITE CLI doesn't support this yet.

    # I update the component aws.gg.uat.cloud.ComponentConfigTestService version 1.0.0 parameter singleLevelKey with value newValueForSingleLevelKey
    # TODO: We do not support merge/reset configuration in local deployment.

    # I can use greengrass-cli component details -n to check the component aws.gg.uat.cloud.ComponentConfigTestService has configuration that is equal to JSON:
    # """
    # {
    #   "emptyListKey": [],
    #   "emptyObjectKey": {},
    #   "emptyStringKey": "",
    #   "listKey": [
    #     "item1",
    #     "item2"
    #   ],
    #   "nestedKey": {
    #     "leafKey": "default value of /nestedKey/leafKey"
    #   },
    #   "singleLevelKey": "newValueForSingleLevelKey",
    #   "willBeNullKey": "I will be set to null soon",
    #   "defaultIsNullKey": null
    # }
    # """
    # GG_LITE CLI doesn't support this yet.

    # I create an empty deployment configuration for deployment SecondCloudDeployment

    # I update the deployment configuration SecondCloudDeployment, setting the component "aws.gg.uat.cloud.ComponentConfigTestService" version "1.0.0" configuration:
    # """
    # {
    #   "RESET": ["/singleLevelKey"]
    # }
    # """

    # I deploy the configuration for deployment SecondCloudDeployment

    # the deployment SecondCloudDeployment completes with SUCCEEDED within 180 seconds

    # I can check the cli to see the status of component aws.gg.uat.cloud.ComponentConfigTestService is FINISHED

    # I can use greengrass-cli component details -n to check the component aws.gg.uat.cloud.ComponentConfigTestService has configuration that is equal to JSON:
    # """
    # {
    #   "emptyListKey": [],
    #   "emptyObjectKey": {},
    #   "emptyStringKey": "",
    #   "listKey": [
    #     "item1",
    #     "item2"
    #   ],
    #   "nestedKey": {
    #     "leafKey": "default value of /nestedKey/leafKey"
    #   },
    #   "singleLevelKey": "default value of singleLevelKey",
    #   "willBeNullKey": "I will be set to null soon",
    #   "defaultIsNullKey": null
    # }
    # """
    # GG_LITE CLI doesn't support this yet.


# As a component developer, I can use automatic cleanup to delete component files further than last two deployments
# Note: Heavily rewritten as GG_LITE only keeps files from the latest version.
def test_Component_34_T4(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I install the component Minimal version 1.0.0 from local store
    component_recipe_dir = "./components/Minimal/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(None, component_recipe_dir,
                                                "Minimal=1.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "Minimal") == "RUNNING":
            break
        sleep_with_log(1)
        timeout -= 1

    # I install the component Minimal version 2.0.0 from local store
    component_recipe_dir = "./components/Minimal/2.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(None, component_recipe_dir,
                                                "Minimal=2.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "Minimal") == "RUNNING":
            break
        sleep_with_log(1)
        timeout -= 1

    # the local files for component Minimal version 2.0.0 should exist
    # TODO: Replace hacky sleep when we can use CLI to verify a local deployment has finished.
    sleep_with_log(30)
    assert gg_util_obj.recipe_for_component_exists("Minimal", "2.0.0")

    # the local files for component Minimal version 1.0.0 should not exist
    assert not gg_util_obj.recipe_for_component_exists("Minimal", "1.0.0")
