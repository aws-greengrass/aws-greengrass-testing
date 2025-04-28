import time
import pytest
from GGTestUtils import GGTestUtils
from SystemInterface import SystemInterface


@pytest.fixture(scope="function")  # Runs for each test function
def gg_util_obj(pytestconfig):
    # Setup an instance of the GGUtils class. It is then passed to the
    # test functions.
    gg_util = GGTestUtils(
        pytestconfig.getoption("ggTestAccount"),
        pytestconfig.getoption("ggTestBucket"),
        pytestconfig.getoption("ggTestRegion"),
        pytestconfig.getoption("ggTestThingGroup"),
    )

    # yield the instance of the class to the tests.
    yield gg_util

    # This section is called AFTER the test is run.

    # Cleanup the artifacts, components etc.
    gg_util.cleanup()


@pytest.fixture(scope="function")  # Runs for each test function
def system_interface(pytestconfig):
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


# As a component developer, I can create Greengrass component that works on my current platform.
def test_Component_12_T1(gg_util_obj, system_interface):
    # I upload component "MultiPlatform" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "MultiPlatform", "1.0.0")

    # And  I create a deployment configuration with components and configuration
    #   | MultiPlatform | 1.0.0 |
    # And   I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And  I can check the cli to see the status of component MultiPlatform is RUNNING
    """ GG LITE CLI DOESN"T SUPPORT THIS YET. """

    # And  the MultiPlatform log eventually contains the line "Hello world!" within 20 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "Hello world! World",
        timeout=20) is True)


# GC developer can create a component with recipes containing s3 artifact. GGC operator can deploy it and artifact can be run.
def test_Component_16_T1(gg_util_obj, system_interface):
    # I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(),
        [component_cloud_name],
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    # Then I can check the cli to see the status of component HelloWorld is RUNNING
    """ GG LITE CLI DOESN"T SUPPORT THIS YET. """

    # Then the HelloWorld log contains the line "Evergreen's dev experience is great!"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name[0] + ".service",
        "Evergreen's dev experience is great!",
        timeout=20,
    ) is True)


# As a component developer, I expect kernel to fail the deployment if the checksum of downloaded artifacts does not match with the checksum in the recipe.
def test_Component_27_T1(gg_util_obj, system_interface):
    # Given I upload component "HelloWorld" version "1.0.0" from the local store
    # And I ensure component "HelloWorld" version "1.0.0" exists on cloud within 120 seconds
    # And kernel registered as a Thing
    # And my device is running the evergreen-kernel
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I corrupt the contents of the component HelloWorld version 1.0.0 in the S3 bucket
    assert gg_util_obj.upload_corrupt_artifacts_to_s3("HelloWorld",
                                                      "1.0.0") is True

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(),
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


# Scenario: FleetStatus-1-T1: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment succeeds
def test_FleetStatus_1_T1(gg_util_obj):
    # When I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(),
        [component_cloud_name],
        "FirstDeployment",
    )["deploymentId"]
    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I can get the thing status as "HEALTHY" with all uploaded components within 60 seconds with groups
    #      | FssThingGroup |
    assert (gg_util_obj.get_ggcore_device_status(
        60, f"{gg_util_obj.get_thing_group()}") == "HEALTHY")


#As a device application owner, I can deploy configuration with updated components to a thing group.
def test_Deployment_3_T1(gg_util_obj, system_interface):
    # I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment Deployment1 with components
    #   | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment Deployment1
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [component_cloud_name],
        "Deployment1")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment1 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        component_cloud_name) == "RUNNING")

    # And I can check the cli to see the component HelloWorld is running with version 1.0.0
    # GG_LITE CLI doesn't support this yet.

    # I upload component "HelloWorld" version "1.0.1" from the local store
    component_cloud_name1 = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.1")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment Deployment1 with components
    #   | HelloWorld | 1.0.1 |
    # And I deploy the configuration for deployment Deployment2
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [component_cloud_name1],
        "Deployment2")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        component_cloud_name1) == "RUNNING")


# Scenario: Deployment-3-T2: As a device application owner, I can deploy configuration to a thing group which removes a component.
def test_Deployment_3_T2(gg_util_obj, system_interface):
    # When I upload component "HelloWorld" version "1.0.0" from the local store
    # Then I ensure component "HelloWorld" version "1.0.0" exists on cloud within 60 seconds
    hello_world_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # When I upload component "SampleComponent" version "1.0.0" from the local store
    # Then I ensure component "SampleComponent" version "1.0.0" exists on cloud within 60 seconds
    sample_component_cloud_name = gg_util_obj.upload_component_with_version(
        "SampleComponent", "1.0.0")

    # When I create a deployment configuration for deployment Deployment1 with components
    #    | HelloWorld      | 1.0.0 |
    #    | SampleComponent | 1.0.0 |
    # And I deploy the configuration for deployment Deployment1
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(),
        [hello_world_cloud_name, sample_component_cloud_name],
        "Deployment1")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment1 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    # And I can check the cli to see the component HelloWorld is running with version 1.0.0
    assert (system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name) == "RUNNING")

    # And I can check the cli to see the status of component SampleComponent is RUNNING
    # And I can check the cli to see the component SampleComponent is running with version 1.0.0
    assert (system_interface.check_systemctl_status_for_component(
        sample_component_cloud_name) == "RUNNING")

    # When I upload component "HelloWorld" version "1.0.1" from the local store
    # Then I ensure component "HelloWorld" version "1.0.1" exists on cloud within 60 seconds
    hello_world_cloud_name_1 = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.1")

    # When I create a deployment configuration for deployment Deployment2 with components
    #    | HelloWorld | 1.0.1 |
    # And I deploy the configuration for deployment Deployment2
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [hello_world_cloud_name_1],
        "Deployment2")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name_1) == "RUNNING")
    # And I can check the cli to see the component HelloWorld is running with version 1.0.1
    # GG CLI doesn't yet support this.

    # And I can check the cli to see the component SampleComponent is not listed
    assert (system_interface.check_systemctl_status_for_component(
        sample_component_cloud_name) == "NOT_RUNNING")


# Scenario: Deployment-3-T3: As a device application owner, if a component is broken and I deploy a fix it should succeed
def test_Deployment_3_T3(gg_util_obj, system_interface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [broken_component_cloud_name],
        "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name) == "NOT_RUNNING")

    # When I upload component "BrokenComponent" version "1.0.2" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.2" exists on cloud within 60 seconds
    broken_component_v2_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.2")

    # And I create a deployment configuration for deployment SecondDeployment with components
    #     | BrokenComponent | 1.0.2 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [broken_component_v2_cloud_name],
        "SecondDeployment")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment SecondDeployment completes with SUCCEEDED within 60 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        60, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component BrokenComponent is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_v2_cloud_name) == "RUNNING")


# Scenario: Deployment-3-T4: As a device application owner, if a component is broken and I deploy a fix that doesn't work it should fail
def test_Deployment_3_T4(gg_util_obj, system_interface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [broken_component_cloud_name],
        "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name) == "NOT_RUNNING")

    # When I upload component "BrokenComponent" version "1.0.1" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.1" exists on cloud within 60 seconds
    broken_component_v1_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.1")

    # And I create a deployment configuration for deployment SecondDeployment with components
    #     | BrokenComponent | 1.0.1 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_v1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [broken_component_v1_cloud_name],
        "SecondDeployment")["deploymentId"]
    assert deployment_id_v1 is not None

    # Then the deployment SecondDeployment completes with FAILED within 60 seconds
    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_v1) == "FAILED")


# Scenario: Deployment-3-T5: As a device application owner, if a component is broken and I deploy a different component it should proceed as usual
def test_Deployment_3_T5(gg_util_obj, system_interface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [broken_component_cloud_name],
        "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name) == "NOT_RUNNING")

    # When I upload component "HelloWorld" version "1.0.0" from the local store
    # Then I ensure component "HelloWorld" version "1.0.0" exists on cloud within 60 seconds
    hello_world_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment Deployment2 with components
    #     | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment Deployment2
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(), [hello_world_cloud_name],
        "Deployment2")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")
