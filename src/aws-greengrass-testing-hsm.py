from typing import Generator
from pytest import fixture, mark
from src.IoTUtils import IoTUtils
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface

import time
import subprocess
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
    ggl_setup.install_greengrass_lite_from_source(commit_id, region)

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


# Scenario: test_HSM_1_T1: As a customer, I want to store the private key for secret encryption in a TPM/HSM
# Given my device has already run the nucleus lite, and the key is stored in the TPM,
# I want to reboot the nucleus lite and test with the MQTT pub/sub on the topic.
@mark.skip(
    reason=
    "This test could only support under NitroTPM or hardware TPM, so please test it locally"
)
def test_HSM_1_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                  system_interface: SystemInterface):
    # Execute generate_tpm_key.sh script
    result = subprocess.run(['bash', 'generate_tpm_key.sh', 'set_up_tpm'],
                            cwd='../aws-greengrass-testing')

    assert result.returncode == 0, f"Script failed with return code: {result.returncode}"

    hsm_thing_name = iot_obj.thing_name
    id = iot_obj.generate_random_id()
    hsm_thing_group_name = iot_obj.generate_thing_group_name(id)
    hsm_thing_group_result = iot_obj.add_thing_to_thing_group(
        hsm_thing_name, hsm_thing_group_name)
    assert hsm_thing_group_result is True

    # When I install the component HelloWorldPubSub version 1.0.0 from local store
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(hsm_thing_group_name),
        component_list=[pubsub_cloud_name],
        deployment_name="FirstDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

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

    # Cleanup TPM key after successful test
    cleanup_result = subprocess.run(
        ['bash', 'generate_tpm_key.sh', 'cleanup_tpm'],
        cwd='../aws-greengrass-testing')
    print(f"TPM cleanup completed")
