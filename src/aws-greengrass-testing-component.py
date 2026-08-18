from typing import Generator
from GGTestUtils import sleep_with_log
from pytest import fixture, mark
from src.IoTUtils import IoTUtils
from src.GGTestUtils import GGTestUtils, ComponentDeploymentInfo
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


# As an operator, when I interpolate component default configurations by local
# deployment, a recipe variable naming a configuration value that does not exist
# is left in the lifecycle script verbatim rather than substituted with an empty
# string, and the rest of the script still runs. See aws-greengrass-lite#867.
def test_Component_29_T5(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    component = "SampleComponentWithMissingConfiguration"
    recipe_dir = f"./components/{component}/1.0.0/recipe/"
    service = f"ggl.{component}.service"

    assert gg_util_obj.create_local_deployment(None, recipe_dir,
                                               f"{component}=1.0.0")

    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                component) == "RUNNING":
            break
        sleep_with_log(1)
        timeout -= 1
    assert (system_interface.check_systemctl_status_for_component(component) ==
            "RUNNING")

    # A resolvable variable is still substituted.
    assert (system_interface.monitor_journalctl_for_message(
        service, "present=MyConfigDefaultValue", timeout=30) is True)

    # An unresolvable variable is left as-is instead of blanked.
    assert (system_interface.monitor_journalctl_for_message(
        service, "missing={configuration:/NoSuchKey}", timeout=30) is True)

    # The line after the unresolvable variable still runs, so interpolation did
    # not truncate the script.
    assert (system_interface.monitor_journalctl_for_message(service,
                                                            "TAIL_REACHED",
                                                            timeout=30) is True)


# As an operator, GetConfiguration and SubscribeToConfigurationUpdate requests
# for aws.greengrass.Nucleus are transparently forwarded to
# aws.greengrass.NucleusLite on Greengrass Lite, and subscribe events are
# relabeled back to the requested aws.greengrass.Nucleus name.
def test_Component_30_T0(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    component_name = "aws.gg.uat.local.NucleusConfigForwardingTestService"
    service = "ggl." + component_name + ".service"
    # Keep in sync with PROBE_KEY in the forwarding component artifact.
    probe_key = "uatForwardingProbe"
    journal_since = time.time()

    # Pre-condition: the test setup (setup_greengrass_lite, run by the iot_obj
    # fixture) seeds iotDataEndpoint under aws.greengrass.NucleusLite
    # configuration, which the component reads back through the Nucleus alias.

    # I install the forwarding test component version 1.0.0 from local store
    component_artifacts_dir = "./components/local_artifacts/"
    component_recipe_dir = (
        "./components/aws.gg.uat.local.NucleusConfigForwardingTestService/"
        "1.0.0/recipe/")
    assert (gg_util_obj.create_local_deployment(component_artifacts_dir,
                                                component_recipe_dir,
                                                component_name + "=1.0.0"))

    # A GetConfiguration read using the classic aws.greengrass.Nucleus name
    # returns the same value as a direct aws.greengrass.NucleusLite read.
    assert (system_interface.monitor_journalctl_for_message(
        service, "NUCLEUS FORWARDING MATCH", timeout=120, since=journal_since)
            is True)

    # The response is transparent: it echoes the requested component name.
    assert (system_interface.monitor_journalctl_for_message(
        service,
        "NUCLEUS RESPONSE COMPONENT NAME MATCH",
        timeout=20,
        since=journal_since) is True)

    # Subscribing via the classic Nucleus name is accepted (forwarded), not
    # rejected with ResourceNotFoundError.
    assert (system_interface.monitor_journalctl_for_message(
        service, "NUCLEUS SUBSCRIBE OK", timeout=20, since=journal_since)
            is True)

    # Drive a NucleusLite configuration change via a cloud deployment and verify
    # the subscribed component receives the update relabeled to
    # aws.greengrass.Nucleus (not the underlying aws.greengrass.NucleusLite). A
    # harmless probe key is merged so no nucleus behavior (e.g. an endpoint
    # switch) is triggered. Cleanup is automatic: the thing group is registered
    # by IoTUtils (removed and deleted in its clean_up) and the deployment by
    # GGTestUtils (cancelled and deleted in its clean_up).
    random_id = iot_obj.generate_random_id()
    thing_group_name = iot_obj.generate_thing_group_name(random_id)
    assert iot_obj.add_thing_to_thing_group(iot_obj.thing_name,
                                            thing_group_name) is True
    thing_group_arn = gg_util_obj.get_thing_group_arn(thing_group_name)

    version = gg_util_obj.create_nucleus_lite_component(iot_obj.thing_name)
    sleep_with_log(5, "waiting for NucleusLite component to be DEPLOYABLE")

    probe_component = ComponentDeploymentInfo(
        name="aws.greengrass.NucleusLite",
        versions=[version],
        merge_config={probe_key: "uat-" + random_id},
    )
    deployment_id = gg_util_obj.create_deployment(
        thingArn=thing_group_arn,
        component_list=[probe_component],
        deployment_name="NucleusConfigForwardingProbe",
    )["deploymentId"]
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # The component subscribed via aws.greengrass.Nucleus must receive the
    # config-update event relabeled to aws.greengrass.Nucleus.
    assert (system_interface.monitor_journalctl_for_message(
        service, "CONFIG UPDATE EVENT componentName=aws.greengrass.Nucleus "
        f"keyPath={probe_key}",
        timeout=120,
        since=journal_since) is True)

    # No event may expose the underlying storage component name. The window is
    # 2x the component's 5s sentinel reprint interval so a late or duplicate
    # mislabeled event would also be caught.
    assert (system_interface.monitor_journalctl_for_message(
        service,
        "CONFIG UPDATE EVENT componentName=aws.greengrass.NucleusLite keyPath=",
        timeout=10,
        since=journal_since) is False)


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


# Regex alternation in os attribute selects the correct manifest on a matching platform.
def test_Component_35_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves regex alternation /linux|darwin/ matches the os attribute on a Linux device."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformAlternation", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ALTERNATION_SELECTED",
        timeout=20) is True)


# Regex whole-string anchoring: /lin/ must NOT match "linux" because matching is anchored.
def test_Component_35_T2(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves whole-string regex matching: /lin/ does not match linux.
    The second manifest is regex-gated (/linux/) so this test fails rather
    than passing vacuously when regex support is absent."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformAnchored", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # Manifest 2 must win: /linux/ matches the device os.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ANCHOR_FALLBACK",
        timeout=20) is True)

    # Negative assertion: the violation marker must NOT appear in the log.
    # A short timeout is used; if monitor returns True, the anchoring is broken.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ANCHOR_VIOLATION",
        timeout=10) is False)


# Regex alternation on the architecture attribute selects the correct manifest.
def test_Component_35_T3(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves regex /aarch64|amd64/ matches the architecture attribute on arm64 or x86_64 devices."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformArch", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ARCH_SELECTED",
        timeout=20) is True)


# Regex matching works on the runtime platform attribute.
def test_Component_35_T4(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves regex /aws_nucleus_lite/ matches the runtime attribute on a Greengrass Lite device."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformRuntime", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_RUNTIME_SELECTED",
        timeout=20) is True)


# A malformed regex pattern must fail closed, selecting the fallback manifest.
def test_Component_35_T5(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves that /(?i)linux/ is valid to the cloud's Java regex validator but
    unsupported by Lite's Thompson NFA engine (no inline-flag support), so
    Lite declines the first manifest and selects the regex-gated second
    manifest (/linux/).  The second manifest is regex-gated so this test
    cannot pass vacuously when regex support is absent."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformMalformed", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # Manifest 2 must win: /linux/ matches the device os.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_MALFORMED_FALLBACK",
        timeout=20) is True)

    # Negative assertion: the malformed pattern must never have matched.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_MALFORMED_ACCEPTED",
        timeout=10) is False)


# Five-branch regex alternation on architecture selects the correct manifest.
def test_Component_35_T6(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves five-branch regex /riscv64|x86|aarch64|amd64|arm/ matches architecture.
    The likely matching branch (aarch64 or amd64) is deliberately not first in the
    alternation, proving the engine traverses all alternatives."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformArchMulti", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ARCH_MULTI_SELECTED",
        timeout=20) is True)

    # Negative assertion: the fallback must NOT have been selected.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ARCH_MULTI_FALLBACK",
        timeout=10) is False)


# Five-branch regex where no branch matches forces fallback selection.
def test_Component_35_T7(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    """Proves /riscv64|x86|arm/ does not match aarch64 or amd64.
    Both manifests are regex-gated so this test fails rather than passing
    vacuously when regex support is absent. Manifest 2 matches /aarch64|amd64/,
    so this test assumes the device reports architecture as aarch64 or amd64.
    It would incorrectly pass on a 32-bit arm, riscv64, or x86 device."""
    new_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    component_cloud_name = gg_util_obj.upload_component_with_versions(
        "RegexPlatformArchMultiNoMatch", ["1.0.0"])

    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    sleep_with_log(5)

    # Manifest 2 must win: /aarch64|amd64/ matches the device architecture.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ARCH_MULTI_NOMATCH_FALLBACK",
        timeout=20) is True)

    # Negative assertion: the violation marker must NOT appear.
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "REGEX_ARCH_MULTI_NOMATCH_VIOLATION",
        timeout=10) is False)
