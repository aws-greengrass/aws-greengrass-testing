import time
import pytest
from src.IoTUtils import IoTTestUtils
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface
from config import config


@pytest.fixture(scope="function")  # Runs for each test function
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


@pytest.fixture(scope="function")  # Runs for each test function
def system_interface():
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


@pytest.fixture(scope="function")  # Runs for each test function
def iot_obj():
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


#As a developer, I can use the local cli to deploy a single component to a device locally without cloud intervention.
def test_Deployment_1_T1(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I check cli to get list of local deployments and verify it has 0 deployments in ANY state
    # GG_LITE CLI doesn't support this yet.

    # I install the component SampleComponentWithConfiguration version 1.0.0 from local store
    component_recipe_dir = "./components/SampleComponentWithConfiguration/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        None, component_recipe_dir, "SampleComponentWithConfiguration=1.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "SampleComponentWithConfiguration") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1

    # I can check the cli to see the status of component SampleComponentWithConfiguration is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        "SampleComponentWithConfiguration") == "RUNNING")

    # I can check the cli to see the component SampleComponentWithConfiguration is running with version 1.0.0
    # GG_LITE CLI doesn't support this yet.

    # I can check the cli to get list of local deployments and verify it has 1 deployments in SUCCEEDED state
    # GG_LITE CLI doesn't support this yet.


#As a developer, I can use the local cli to deploy multiple components to a device locally without cloud intervention.
def test_Deployment_1_T2(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I install the following components from local store
    #   | SampleComponentWithConfiguration | 1.0.0 |
    #   | SampleComponentWithArtifacts     | 1.0.0 |
    component_recipe_dir = "./components/SampleComponentWithConfiguration/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        None, component_recipe_dir, "SampleComponentWithConfiguration=1.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "SampleComponentWithConfiguration") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1

    component_recipe_dir = "./components/SampleComponentWithArtifacts/1.0.0/recipe/"
    component_artifacts_dir = "./components/SampleComponentWithArtifacts/1.0.0/artifacts/"
    assert (gg_util_obj.create_local_deployment(
        component_artifacts_dir, component_recipe_dir,
        "SampleComponentWithArtifacts=1.0.0"))
    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 120
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "SampleComponentWithArtifacts") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1

    # I can check the cli to see the status of component SampleComponentWithConfiguration is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        "SampleComponentWithConfiguration") == "RUNNING")

    # I can check the cli to see the component SampleComponentWithConfiguration is running with version 1.0.0
    # GG_LITE CLI doesn't support this yet.

    # I can check the cli to see the status of component SampleComponentWithArtifacts is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        "SampleComponentWithArtifacts") == "RUNNING")

    # I can check the cli to see the component SampleComponentWithArtifacts is running with version 1.0.0
    # GG_LITE CLI doesn't support this yet.


# As a developer, I can use the local cli to deploy a single component with component configuration to a device locally without cloud intervention.
# TODO: Update test when merge/reset is supported for local deployments.
# Test is modified to read default config instead of the merge config, since merge/reset configuration is not supported for local deployment yet in GG_LITE
def test_Deployment_1_T3(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I install the component SampleComponentWithConfiguration version 1.0.0 from local store with configuration
    #   | key                                          | value          |
    #   | SampleComponentWithConfiguration:MyConfigKey | NewConfigValue |
    component_recipe_dir = "./components/SampleComponentWithArtifacts/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        None, component_recipe_dir, "SampleComponentWithConfiguration=1.0.0")
            is True)

    # TODO: We can use the CLI to verify that a local deployment has finished once that feature exists
    # For now, check if the expected component is running within a timeout.
    timeout = 180
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "SampleComponentWithConfiguration") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1

    # I can check the cli to see the status of component SampleComponentWithConfiguration is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        "SampleComponentWithConfiguration") == "RUNNING")

    # I can check the cli to see the component SampleComponentWithConfiguration is running with version 1.0.0
    # GG_LITE CLI doesn't support this yet.

    # the SampleComponentWithConfiguration log eventually contains the line "running generic sample with version 1.0.0 with configuration value NewConfigValue" within 60 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.SampleComponentWithConfiguration.service",
        "running generic sample with version 1.0.0 with configuration value MyConfigDefaultValue",
        timeout=20) is True)


#As a device application owner, I can deploy configuration with updated components to a thing group.
def test_Deployment_3_T1(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment Deployment1 with components
    #   | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment Deployment1
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [component_cloud_name], "Deployment1")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment1 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        component_cloud_name[0]) == "RUNNING")

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
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [component_cloud_name1], "Deployment2")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        component_cloud_name1[0]) == "RUNNING")


# Scenario: Deployment-3-T2: As a device application owner, I can deploy configuration to a thing group which removes a component.
def test_Deployment_3_T2(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
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
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [hello_world_cloud_name, sample_component_cloud_name],
        "Deployment1")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment1 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    # And I can check the cli to see the component HelloWorld is running with version 1.0.0
    assert (system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name[0]) == "RUNNING")

    # And I can check the cli to see the status of component SampleComponent is RUNNING
    # And I can check the cli to see the component SampleComponent is running with version 1.0.0
    assert (system_interface.check_systemctl_status_for_component(
        sample_component_cloud_name[0]) == "RUNNING")

    # When I upload component "HelloWorld" version "1.0.1" from the local store
    # Then I ensure component "HelloWorld" version "1.0.1" exists on cloud within 60 seconds
    hello_world_cloud_name_1 = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.1")

    # When I create a deployment configuration for deployment Deployment2 with components
    #    | HelloWorld | 1.0.1 |
    # And I deploy the configuration for deployment Deployment2
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [hello_world_cloud_name_1], "Deployment2")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component HelloWorld is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name_1[0]) == "RUNNING")
    # And I can check the cli to see the component HelloWorld is running with version 1.0.1
    # GG CLI doesn't yet support this.

    # And I can check the cli to see the component SampleComponent is not listed
    assert (system_interface.check_systemctl_status_for_component(
        sample_component_cloud_name[0]) == "NOT_RUNNING")


# Scenario: Deployment-3-T3: As a device application owner, if a component is broken and I deploy a fix it should succeed
def test_Deployment_3_T3(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_cloud_name], "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name[0]) == "NOT_RUNNING")

    # When I upload component "BrokenComponent" version "1.0.2" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.2" exists on cloud within 60 seconds
    broken_component_v2_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.2")

    # And I create a deployment configuration for deployment SecondDeployment with components
    #     | BrokenComponent | 1.0.2 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_v2_cloud_name], "SecondDeployment")["deploymentId"]
    assert deployment_id_2 is not None

    # Then the deployment SecondDeployment completes with SUCCEEDED within 60 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        60, deployment_id_2) == "SUCCEEDED")

    # And I can check the cli to see the status of component BrokenComponent is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_v2_cloud_name[0]) == "RUNNING")


# Scenario: Deployment-3-T4: As a device application owner, if a component is broken and I deploy a fix that doesn't work it should fail
def test_Deployment_3_T4(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_cloud_name], "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name[0]) == "NOT_RUNNING")

    # When I upload component "BrokenComponent" version "1.0.1" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.1" exists on cloud within 60 seconds
    broken_component_v1_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.1")

    # And I create a deployment configuration for deployment SecondDeployment with components
    #     | BrokenComponent | 1.0.1 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_v1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_v1_cloud_name], "SecondDeployment")["deploymentId"]
    assert deployment_id_v1 is not None

    # Then the deployment SecondDeployment completes with FAILED within 60 seconds
    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_v1) == "FAILED")


# Scenario: Deployment-3-T5: As a device application owner, if a component is broken and I deploy a different component it should proceed as usual
def test_Deployment_3_T5(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # When I upload component "BrokenComponent" version "1.0.0" from the local store
    # Then I ensure component "BrokenComponent" version "1.0.0" exists on cloud within 60 seconds
    broken_component_cloud_name = gg_util_obj.upload_component_with_version(
        "BrokenComponent", "1.0.0")

    # And I create a deployment configuration for deployment FirstDeployment with components
    #     | BrokenComponent | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [broken_component_cloud_name], "FirstDeployment")["deploymentId"]

    assert deployment_id is not None

    # Then the deployment FirstDeployment completes with FAILED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # And I wait for 10 seconds
    time.sleep(10)

    # And I can check the cli to see the status of component BrokenComponent is BROKEN
    # GG LITE CLI cannot yet do this, so we rely on systemctl.
    assert (system_interface.check_systemctl_status_for_component(
        broken_component_cloud_name[0]) == "NOT_RUNNING")

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
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [hello_world_cloud_name], "Deployment2")["deploymentId"]
    assert deployment_id_1 is not None

    # Then the deployment Deployment2 completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")


# Scenario: Deployment-5-T2: As a device application owner, I can remove a common component from one of the group the device belongs to from an IoT Jobs deployment
def test_Deployment_5_T2(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface):
    # When I upload component "Component2BaseCloud" version "1.0.0" from the local store
    # Then I ensure component "Component2BaseCloud" version "1.0.0" exists on cloud within 60 seconds
    Component2BaseCloud_cloud_name = gg_util_obj.upload_component_with_version(
        "Component2BaseCloud", "1.0.0")

    # When I create a deployment configuration for deployment FirstDeployment and thing group FirstThingGroup with components
    #     | Component2BaseCloud | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [Component2BaseCloud_cloud_name], "FirstDeployment")["deploymentId"]

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # When I create a deployment configuration for deployment SecondDeployment and thing group NewThingGroup with components
    #     | Component2BaseCloud | 1.0.0 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_2 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_2),
        [Component2BaseCloud_cloud_name], "SecondDeployment")["deploymentId"]

    # Then the deployment SecondDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_2) == "SUCCEEDED")

    # Then I can check the cli to see the status of component Component2BaseCloud is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        Component2BaseCloud_cloud_name[0]) == "RUNNING"

    #     # This following step removes the Component2BaseCloud from the first group
    # When I create an empty deployment configuration for deployment ThirdDeployment and thing group FirstThingGroup
    # And I deploy the configuration for deployment ThirdDeployment
    deployment_id_3 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1), [],
        "ThirdDeployment")["deploymentId"]

    # Then the deployment ThirdDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_3) == "SUCCEEDED")

    # Then I can check the cli to see the status of component Component2BaseCloud is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        Component2BaseCloud_cloud_name[0]) == "RUNNING"


# Scenario: Deployment-7-T3: As a device application owner, I can deploy from IoT Jobs different set of components to the device belonging to multiple thing groups
def test_Deployment_7_T3(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface,
                         iot_obj: IoTTestUtils):
    # When I upload component "Component2BaseCloud" version "1.0.0" from the local store
    # Then I ensure component "Component2BaseCloud" version "1.0.0" exists on cloud within 60 seconds
    Component2BaseCloud_cloud_name = gg_util_obj.upload_component_with_version(
        "Component2BaseCloud", "1.0.0")

    # When I create a deployment configuration for deployment FirstDeployment and thing group FirstThingGroup with components
    #     | Component2BaseCloud | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [Component2BaseCloud_cloud_name], "FirstDeployment")["deploymentId"]

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # When I create a new thing group NewThingGroup
    new_thing_group = iot_obj.create_new_thing_group("NewThingGroup")
    assert new_thing_group is not None

    # And I add the device to thing group NewThingGroup
    assert iot_obj.add_thing_to_thing_group(config.thing_name,
                                            new_thing_group) is True

    # Then my device is in following thing group
    #     | FirstThingGroup |
    #     | NewThingGroup   |
    assert iot_obj.is_thing_in_thing_groups(
        config.thing_name, [config.thing_group_1, new_thing_group]) is True

    # When I upload component "HelloWorld" version "1.0.0" from the local store
    # Then I ensure component "HelloWorld" version "1.0.0" exists on cloud within 60 seconds
    hello_world_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # When I create a deployment configuration for deployment SecondDeployment and thing group NewThingGroup with components
    #     | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group),
        [hello_world_cloud_name], "SecondDeployment")["deploymentId"]

    # Then the deployment SecondDeployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id_1) == "SUCCEEDED")

    # Then I can check the cli to see the status of component HelloWorld is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name[0]) == "RUNNING"
    # Then I can check the cli to see the status of component Component2BaseCloud is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        Component2BaseCloud_cloud_name[0]) == "RUNNING"


# Scenario: Deployment-7-T4: As a device application owner, I can deploy from IoT Jobs a common component to thing groups the device belongs to
def test_Deployment_7_T4(gg_util_obj: GGTestUtils,
                         system_interface: SystemInterface,
                         iot_obj: IoTTestUtils):
    # When I upload component "Component2BaseCloud" version "1.0.0" from the local store
    # Then I ensure component "Component2BaseCloud" version "1.0.0" exists on cloud within 60 seconds
    Component2BaseCloud_cloud_name = gg_util_obj.upload_component_with_version(
        "Component2BaseCloud", "1.0.0")

    # When I create a deployment configuration for deployment FirstDeployment and thing group FirstThingGroup with components
    #     | Component2BaseCloud | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [Component2BaseCloud_cloud_name], "FirstDeployment")["deploymentId"]

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # When I create a new thing group NewThingGroup
    new_thing_group = iot_obj.create_new_thing_group("NewThingGroup")
    assert new_thing_group is not None

    # And I add the device to thing group NewThingGroup
    assert iot_obj.add_thing_to_thing_group(config.thing_name,
                                            new_thing_group) is True

    # Then my device is in following thing group
    #     | FirstThingGroup |
    #     | NewThingGroup   |
    assert iot_obj.is_thing_in_thing_groups(
        config.thing_name, [config.thing_group_1, new_thing_group]) is True

    # When I upload component "HelloWorld" version "1.0.0" from the local store
    # Then I ensure component "HelloWorld" version "1.0.0" exists on cloud within 60 seconds
    hello_world_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # When I create a deployment configuration for deployment SecondDeployment and thing group NewThingGroup with components
    #     | HelloWorld | 1.0.0 |
    #     | Component2BaseCloud | 1.0.0 |
    # And I deploy the configuration for deployment SecondDeployment
    deployment_id_1 = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(new_thing_group),
        [hello_world_cloud_name, Component2BaseCloud_cloud_name],
        "SecondDeployment")["deploymentId"]

    # Then the deployment SecondDeployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id_1) == "SUCCEEDED")

    # Then I can check the cli to see the status of component HelloWorld is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        hello_world_cloud_name[0]) == "RUNNING"

    # Then I can check the cli to see the status of component Component2BaseCloud is RUNNING
    assert system_interface.check_systemctl_status_for_component(
        Component2BaseCloud_cloud_name[0]) == "RUNNING"
