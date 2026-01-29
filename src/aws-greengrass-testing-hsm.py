from typing import Generator
from pytest import fixture, mark
from src.IoTUtils import IoTUtils
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface

import time
import subprocess
import sqlite3
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

    # Check if the test is requesting to use the fleet provisioning
    fleet_provisioning = getattr(request, 'param',
                                 {}).get('fleet_provisioning', False)

    if fleet_provisioning:
        # Only download source for fleet provisioning tests
        ggl_setup.download_greengrass_lite(commit_id)
    else:
        # Full setup for normal tests
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


# Scenario: test_HSM_2_T1: As a customer, I want nucleus lite to use a TPM-stored claim key
# during fleet provisioning and continue to use that same TPM key after reboot,
# so that my device identity and MQTT connectivity persist securely without exporting keys.
@mark.skip(
    reason=
    "This test could only support under NitroTPM or hardware TPM, so please test it locally"
)
# @mark.parametrize('iot_obj', [{'fleet_provisioning': True}], indirect=True)
def test_HSM_2_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                  system_interface: SystemInterface):
    # Setup fleet provisioning with TPM
    result = subprocess.run(
        ['bash', 'fleet_provisioning_tpm.sh', 'setup_fleet_provisioning'],
        cwd='../aws-greengrass-testing')
    assert result.returncode == 0, f"Fleet provisioning setup failed with return code: {result.returncode}"

    # Get the provisioned thing name from the database
    db_path = '/var/lib/greengrass/config.db'
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Get keyid for 'thingName' from keyTable
    cursor.execute("SELECT keyid FROM keyTable WHERE keyvalue='thingName'")
    key_result = cursor.fetchone()

    if not key_result:
        conn.close()
        raise AssertionError("thingName key not found in keyTable")

    keyid = key_result[0]

    # Get the actual thing name value from valueTable using keyid
    cursor.execute("SELECT value FROM valueTable WHERE keyid=?", (keyid, ))
    value_result = cursor.fetchone()
    conn.close()

    provisioned_thing_name = value_result[0] if value_result else None
    assert provisioned_thing_name, "Thing name not found in valueTable after fleet provisioning"

    # Strip quotes if present
    hsm_thing_name = provisioned_thing_name.strip('"')
    print(f"Provisioned thing name: {hsm_thing_name}")

    hsm_thing_group_name = "GreengrassDevices-TPM"

    # Deploy HelloWorldPubSub component
    pubsub_cloud_name = gg_util_obj.upload_component_with_versions(
        "HelloWorldPubSub", ["1.0.0"])

    if pubsub_cloud_name is None:
        raise RuntimeError(
            "Fatal error: HelloWorldPubSub cannot be uploaded to cloud")

    deployment_id = gg_util_obj.create_deployment(
        thingArn=gg_util_obj.get_thing_group_arn(hsm_thing_group_name),
        component_list=[pubsub_cloud_name],
        deployment_name="FleetProvisioningDeployment")["deploymentId"]

    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # Verify MQTT connectivity with TPM key
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully subscribed to test/topic",
        timeout=60) is True)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully published to test/topic",
        timeout=60) is True)

    # Reboot to verify TPM key persistence
    print("Restarting Greengrass to verify TPM key persistence...")
    assert system_interface.restart_systemd_nucleus_lite(timeout=60) is True
    time.sleep(10)

    # Verify MQTT connectivity persists after reboot
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully subscribed to test/topic",
        timeout=60) is True)

    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + pubsub_cloud_name[0] + ".service",
        "Successfully published to test/topic",
        timeout=60) is True)

    # Cleanup
    cleanup_result = subprocess.run(
        ['bash', 'fleet_provisioning_tpm.sh', 'cleanup_fleet_provisioning'],
        cwd='../aws-greengrass-testing')
