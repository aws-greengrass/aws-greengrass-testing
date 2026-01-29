from typing import Generator
from GGTestUtils import sleep_with_log
from pytest import fixture
import pytest
from src.IoTUtils import IoTUtils
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface

import time
import boto3
import src.GGLSetup as ggl_setup


@fixture(scope="function")
def gg_util_obj(request) -> Generator[GGTestUtils, None, None]:
    aws_account = request.config.getoption("--aws-account")
    s3_bucket = request.config.getoption("--s3-bucket")
    region = request.config.getoption("--region")
    ggl_cli_path = request.config.getoption("--ggl-cli-path")

    gg_util_obj = GGTestUtils(aws_account, s3_bucket, region, ggl_cli_path)

    yield gg_util_obj

    # CloudWatch cleanup should happen before GGTestUtils cleanup
    # to avoid the SystemLogForwarder component deleting log groups
    gg_util_obj.cleanup()


@fixture(scope="function")
def iot_obj(request) -> Generator[IoTUtils, None, None]:
    region = request.config.getoption("--region")
    commit_id = request.config.getoption("--commit-id")
    iot_obj = IoTUtils(region)

    print(f"Setting up IoT core device...")
    iot_obj.set_up_core_device()
    print(f"Installing GGL from source with commit {commit_id}...")
    ggl_setup.setup_greengrass_lite(commit_id, region)
    print(f"GGL setup complete")

    yield iot_obj

    print(f"Cleaning up GGL and IoT resources...")
    ggl_setup.clean_up()
    iot_obj.clean_up()


@fixture(scope="function")
def system_interface() -> Generator[SystemInterface, None, None]:
    interface = SystemInterface()
    yield interface


@fixture(scope="function")
def cloudwatch_cleanup(request) -> Generator[None, None, None]:
    region = request.config.getoption("--region")
    logs_client = boto3.client('logs', region_name=region)

    # Store cleanup info with unique log group per test
    import time
    test_id = f"{int(time.time())}-{request.node.name}"
    log_group_name = f"greengrass/systemLogs-{test_id}"
    cleanup_info = {'log_stream_name': None, 'log_group_name': log_group_name}

    yield cleanup_info

    # Cleanup after test
    print(f"Starting CloudWatch cleanup...")
    try:
        log_group_name = cleanup_info.get('log_group_name')
        log_stream_name = cleanup_info.get('log_stream_name')
        print(f"Log stream name for cleanup: {log_stream_name}")
        print(f"Log group for cleanup: {log_group_name}")

        if log_stream_name:
            try:
                logs_client.delete_log_stream(logGroupName=log_group_name,
                                              logStreamName=log_stream_name)
                print(f"Deleted log stream: {log_stream_name}")
            except Exception as cleanup_e:
                if "ResourceNotFoundException" in str(cleanup_e):
                    print(
                        f"Log stream {log_stream_name} or log group {log_group_name} does not exist"
                    )
                else:
                    print(f"Failed to delete log stream: {cleanup_e}")

        # Check if log group exists before trying to clean it up
        # TODO: Only delete log group if it was created during the test--check if the log group exists before deploying SLF.
        try:
            response = logs_client.describe_log_groups(
                logGroupNamePrefix=log_group_name)
            log_groups = response.get('logGroups', [])

            if not log_groups:
                print(
                    f"Log group {log_group_name} does not exist, skipping cleanup"
                )
                return

            print(f"Log group {log_group_name} exists, proceeding with cleanup")

            try:
                logs_client.delete_log_group(logGroupName=log_group_name)
                print(f"Deleted log group: {log_group_name}")
            except Exception as cleanup_e:
                if "ResourceNotFoundException" in str(cleanup_e):
                    print(f"Log group {log_group_name} was already deleted")
                else:
                    print(f"Failed to delete log group: {cleanup_e}")

        except Exception as e:
            print(f"Failed to check log group existence: {e}")
    except Exception as e:
        print(f"CloudWatch cleanup failed: {e}")


# Scenario: SLF-1-T1: As a device application owner, I can deploy SLF to my device with default configuration values and the component will be healthy and the deployment succeeds.
@pytest.mark.skip(reason="System log forwarder binaries not available")
def test_SLF_1_T1(iot_obj: IoTUtils, cloudwatch_cleanup,
                  gg_util_obj: GGTestUtils, system_interface: SystemInterface):
    # Store log stream name for cleanup - use default log group name
    cloudwatch_cleanup['log_stream_name'] = iot_obj.thing_name
    cloudwatch_cleanup['log_group_name'] = "greengrass/systemLogs"

    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    random_id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(random_id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # When I upload component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" from the local store
    # Then I ensure component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" exists on cloud within 60 seconds
    slf_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "aws.greengrass.SystemLogForwarderTest", ["0.1.0"])
    print(f"Component uploaded: {slf_component_cloud_name}")

    # Give time for cloud to process artifacts and make component deployable
    sleep_with_log(10)
    print(f"Waited 10 seconds for artifact processing")

    # Check component status
    try:
        component_status = gg_util_obj._ggClient.describe_component(
            arn=
            f"arn:aws:greengrass:{gg_util_obj._region}:{gg_util_obj._account}:components:{slf_component_cloud_name.name}:versions:0.1.0"
        )
        print(f"Component status: {component_status.get('status', 'UNKNOWN')}")
    except Exception as e:
        print(f"Could not check component status: {e}")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | aws.greengrass.SystemLogForwarderTest | 0.1.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [slf_component_cloud_name], "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I wait for 10 seconds
    sleep_with_log(10)

    # And I can check the cli to see the status of component SystemLogForwarderTest is RUNNING
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        slf_component_cloud_name.name) == "RUNNING")


# Scenario: SLF-1-T2: As a device application owner, I can deploy SLF to my device with default filter configuration and reduced time-based configuration and observe logs show up in the cloud.
@pytest.mark.skip(reason="System log forwarder binaries not available")
def test_SLF_1_T2(iot_obj: IoTUtils, cloudwatch_cleanup,
                  gg_util_obj: GGTestUtils, system_interface: SystemInterface):
    # Store log stream name for cleanup
    cloudwatch_cleanup['log_stream_name'] = iot_obj.thing_name
    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    random_id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(random_id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # When I upload component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" from the local store
    # Then I ensure component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" exists on cloud within 60 seconds
    slf_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "aws.greengrass.SystemLogForwarderTest", ["0.1.0"])
    print(f"Component uploaded: {slf_component_cloud_name}")

    # Give time for cloud to process artifacts and make component deployable
    sleep_with_log(10)
    print(f"Waited 10 seconds for artifact processing")

    # And I apply reduced time configuration with unique log group
    slf_component_cloud_name = slf_component_cloud_name._replace(
        merge_config={
            "maxUploadIntervalSec": 10,
            "logGroup": cloudwatch_cleanup['log_group_name']
        })

    # And I create a deployment configuration for deployment SLFDeployment with components
    #     | aws.greengrass.SystemLogForwarderTest | 0.1.0 |
    # And I deploy the configuration for deployment SLFDeployment with reduced time configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [slf_component_cloud_name], "SLFDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment SLFDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I wait for 10 seconds
    sleep_with_log(10)

    # And I can check the cli to see the status of component SystemLogForwarderTest is RUNNING
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        slf_component_cloud_name.name) == "RUNNING")

    # Skip the journalctl monitoring for now to avoid excessive logging
    print(f"Skipping journalctl monitoring to avoid log spam")
    # assert (system_interface.monitor_journalctl_for_message(
    #     f"ggl.{slf_component_cloud_name.name}.service",
    #     "Starting log forwarder",
    #     timeout=60) is True)

    # The SystemLogForwarder itself should be generating logs as a ggl.* service
    # which will be captured by its own filter. Let's wait a bit for it to generate logs
    print(f"SystemLogForwarder should be generating its own logs...")
    sleep_with_log(5)

    # Wait for logs to be uploaded to CloudWatch (maxUploadIntervalSec is 10)
    sleep_with_log(15)
    print(f"Waiting for logs to appear in CloudWatch...")

    # Check CloudWatch logs for system logs
    logs_client = boto3.client('logs', region_name=gg_util_obj._region)
    log_group_name = cloudwatch_cleanup['log_group_name']
    log_stream_name = iot_obj.thing_name

    try:
        response = logs_client.get_log_events(logGroupName=log_group_name,
                                              logStreamName=log_stream_name,
                                              limit=10)
        log_events = response.get('events', [])
        print(f"Found {len(log_events)} log events in CloudWatch")

        # Verify we have some log events
        assert log_events, "No log events found in CloudWatch"

        # Print first few log events for debugging
        for event in log_events[:3]:
            print(f"Log event: {event.get('message', '')[:100]}...")

        # CloudWatch cleanup will be handled by the fixture

    except Exception as e:
        print(f"CloudWatch logs check failed: {e}")
        raise


# Scenario: SLF-1-T3: As a device application owner, I can deploy SLF to my device with a configured log group name.
@pytest.mark.skip(reason="System log forwarder binaries not available")
def test_SLF_1_T3(iot_obj: IoTUtils, cloudwatch_cleanup,
                  gg_util_obj: GGTestUtils, system_interface: SystemInterface):
    # Store log stream name for cleanup
    cloudwatch_cleanup['log_stream_name'] = iot_obj.thing_name
    # Use the log group name from cleanup fixture (which is already unique)
    custom_log_group = cloudwatch_cleanup['log_group_name']

    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    random_id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(random_id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # When I upload component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" from the local store
    slf_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "aws.greengrass.SystemLogForwarderTest", ["0.1.0"])
    print(f"Component uploaded: {slf_component_cloud_name}")

    # Configure component with custom log group name and shorter upload interval
    slf_component_cloud_name = slf_component_cloud_name._replace(
        merge_config={
            "logGroup": custom_log_group,
            "maxUploadIntervalSec":
            10    # Upload every 10 seconds instead of default 300
        })

    # Give time for cloud to process artifacts and make component deployable
    sleep_with_log(10)
    print(f"Waited 10 seconds for artifact processing")

    # Deploy the configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [slf_component_cloud_name],
        "SLFCustomLogGroupDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I wait for 10 seconds
    sleep_with_log(10)

    # And I can check the cli to see the status of component SystemLogForwarderTest is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        slf_component_cloud_name.name) == "RUNNING")

    # The SystemLogForwarder itself should be generating logs as a ggl.* service
    # which will be captured by its own filter. Let's wait a bit for it to generate logs
    print(f"SystemLogForwarder should be generating its own logs...")
    sleep_with_log(5)

    # Wait for logs to be uploaded to CloudWatch (maxUploadIntervalSec is 10)
    sleep_with_log(15)
    print(f"Waiting for logs to appear in CloudWatch...")

    # Verify the custom log group was created by SystemLogForwarder
    logs_client = boto3.client('logs', region_name=gg_util_obj._region)

    try:
        response = logs_client.describe_log_groups(
            logGroupNamePrefix=custom_log_group)
        log_groups = response.get('logGroups', [])

        # Verify the custom log group exists
        assert log_groups, f"Custom log group {custom_log_group} was not created by SystemLogForwarder component"
        print(
            f"Custom log group {custom_log_group} successfully created by SystemLogForwarder"
        )

        # CloudWatch cleanup will be handled by the fixture

    except Exception as e:
        print(f"CloudWatch log group verification failed: {e}")
        raise


# Scenario: SLF-1-T4: As a device application owner, I can deploy SLF to my device with a configured log stream name.
@pytest.mark.skip(reason="System log forwarder binaries not available")
def test_SLF_1_T4(iot_obj: IoTUtils, cloudwatch_cleanup,
                  gg_util_obj: GGTestUtils, system_interface: SystemInterface):
    # Store custom log stream name for cleanup
    custom_log_stream = f"custom-stream-{int(time.time())}"
    cloudwatch_cleanup['log_stream_name'] = custom_log_stream

    # Get an auto generated thing group to which the thing is added.
    new_thing_name = iot_obj.thing_name
    random_id = iot_obj.generate_random_id()
    new_thing_group_name = iot_obj.generate_thing_group_name(random_id)
    new_thing_group_result = iot_obj.add_thing_to_thing_group(
        new_thing_name, new_thing_group_name)
    assert new_thing_group_result is True

    # When I upload component "aws.greengrass.SystemLogForwarderTest" version "0.1.0" from the local store
    slf_component_cloud_name = gg_util_obj.upload_component_with_versions(
        "aws.greengrass.SystemLogForwarderTest", ["0.1.0"])
    print(f"Component uploaded: {slf_component_cloud_name}")

    # Configure component with custom log stream name and shorter upload interval
    slf_component_cloud_name = slf_component_cloud_name._replace(
        merge_config={
            "logGroup": cloudwatch_cleanup['log_group_name'],
            "logStream": custom_log_stream,
            "maxUploadIntervalSec":
            10    # Upload every 10 seconds instead of default 300
        })

    # Give time for cloud to process artifacts and make component deployable
    sleep_with_log(10)
    print(f"Waited 10 seconds for artifact processing")

    # Deploy the configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group_name),
        [slf_component_cloud_name],
        "SLFCustomLogStreamDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I wait for 10 seconds
    sleep_with_log(10)

    # And I can check the cli to see the status of component SystemLogForwarderTest is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        slf_component_cloud_name.name) == "RUNNING")

    # The SystemLogForwarder itself should be generating logs as a ggl.* service
    # which will be captured by its own filter. Let's wait a bit for it to generate logs
    print(f"SystemLogForwarder should be generating its own logs...")
    sleep_with_log(5)

    # Wait for logs to be uploaded to CloudWatch (maxUploadIntervalSec is 10)
    sleep_with_log(15)
    print(f"Waiting for logs to appear in CloudWatch...")

    # Verify the custom log stream was created by SystemLogForwarder
    logs_client = boto3.client('logs', region_name=gg_util_obj._region)

    try:
        response = logs_client.describe_log_streams(
            logGroupName=cloudwatch_cleanup['log_group_name'],
            logStreamNamePrefix=custom_log_stream)
        log_streams = response.get('logStreams', [])

        # Verify the custom log stream exists
        assert log_streams, f"Custom log stream {custom_log_stream} was not created by SystemLogForwarder component"
        print(
            f"Custom log stream {custom_log_stream} successfully created by SystemLogForwarder"
        )

        # CloudWatch cleanup will be handled by the fixture

    except Exception as e:
        print(f"CloudWatch log stream verification failed: {e}")
        raise
