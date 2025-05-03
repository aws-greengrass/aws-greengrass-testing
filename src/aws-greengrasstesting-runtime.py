import time
from pytest import fixture
from pytest import mark
from src.GGTestUtils import GGTestUtils
from src.SystemInterface import SystemInterface
from config import config


@fixture(scope="function")    # Runs for each test function
def gg_util_obj():
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
def system_interface():
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


#Scenario: Runtime-1-T4: As a component developer, if a state transition keeps timing out, then I expect my component
# to be in BROKEN
@mark.skip(reason="TODO: Needs a good way to look up the TIMEOUT log")
def test_Runtime_1_T4(gg_util_obj, system_interface):
    # When I install the component state_transition_timeout version 1.0.0 from local store
    state_transition_timeout = gg_util_obj.upload_component_with_version(
        "state_transition_timeout", "1.0.0")
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [state_transition_timeout], "FirstDeployment")["deploymentId"]
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    # Then I can check state_transition_timeout is in state BROKEN within 60 seconds
    assert system_interface.check_systemctl_status_for_component(
        state_transition_timeout[0]) == "NOT_RUNNING"

    #TODO: Monitor timeout logs
    # And the greengrass log eventually contains the following patterns within 5 seconds
    #   | service-errored.*reason=Timeout in install, serviceName=state_transition_timeout | 2 | should retry 3 times and become broken |
    assert (system_interface.monitor_journalctl_for_message(
        state_transition_timeout[0],
        "Failed with result 'timeout'",
        timeout=30,
    ) is True)


# Scenario: Runtime-1-T5: As a component developer, if my foreground component without IPC exits with a non-zero exit
#   code, then it will be put into the ERRORED state. If the component is in the ERRORED state 3 times, then it will be
#   put into BROKEN.
@mark.skip(
    reason="There isn't currently good way to test transient Errored state")
def test_Runtime_1_T5(gg_util_obj, system_interface):
    #When I install the component foreground_no_ipc_error version 1.0.0 from local store
    foreground_no_ipc_error = gg_util_obj.upload_component_with_version(
        "foreground_no_ipc_error", "1.0.0")
    deployment_id = gg_util_obj.create_deployment(
        gg_util_obj.get_thing_group_arn(config.thing_group_1),
        [foreground_no_ipc_error], "FirstDeployment")["deploymentId"]
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "FAILED")

    #Then I can check foreground_no_ipc_error is in state BROKEN within 30 seconds
    #And the greengrass log eventually contains the following patterns within 5 seconds
    #  | service-set-state.*serviceName=foreground_no_ipc_error, currentState=RUNNING, newState=ERROR | 2 | should retry 3 times and become broken |


# Scenario: Runtime-1-T9: I can install a component with a soft dependency locally
def test_Runtime_1_T9(gg_util_obj, system_interface):
    # When I install the component component_with_soft_dep version 1.0.0 from local store
    component_recipe_dir = "./components/component_with_soft_dep/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        None, component_recipe_dir, "component_with_soft_dep=1.0.0"))

    # Then I can check component_with_soft_dep is in state RUNNING within 30 seconds
    timeout = 30
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "component_with_soft_dep") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1

    # And I can check broken_soft_dep is in state BROKEN within 10 seconds
    timeout = 10
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "broken_soft_dep") == "NOT_RUNNING":
            break
        time.sleep(1)
        timeout -= 1


# Scenario: Runtime-25-T1: As a device application owner, I can expect Greengrass-owner components being robust and can
# recover from unexpected failures such as kernel reboot
def test_Runtime_25_T1(gg_util_obj, system_interface):
    # Given my device is running the evergreen-kernel
    # When I install the component SampleComponentWithArtifacts version 1.0.0 from local store
    component_artifacts_dir = "./components/local_artifacts/"
    component_recipe_dir = "./components/SampleComponentWithArtifacts/1.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        component_artifacts_dir, component_recipe_dir,
        "SampleComponentWithArtifacts=1.0.0"))
    # And I can check SampleComponentWithArtifacts is in state RUNNING within 30 seconds
    timeout = 30
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "SampleComponentWithArtifacts") == "RUNNING":
            break
        time.sleep(1)
        timeout -= 1
    # When I kill the kernel
    sucess_status = system_interface.stop_systemd_nucleus_lite(30)

    # And I start the kernel
    sucess_status = system_interface.start_systemd_nucleus_lite(30)
    # Then I can check the cli to see the status of component SampleComponentWithArtifacts is RUNNING
    assert (system_interface.check_systemctl_status_for_component(
        "SampleComponentWithArtifacts") == "RUNNING")
    # # And No errors were logged


# Scenario: Runtime-28-T3: As a DO, I can run component as privileged user.
def test_Runtime_28_T3(gg_util_obj, system_interface):
    # Given my device is running the evergreen-kernel
    # And I install the component process_status_component_privilege version 0.0.0 from local store
    component_recipe_dir = "./components/process_status_component_privilege/0.0.0/recipe/"
    assert (gg_util_obj.create_local_deployment(
        None, component_recipe_dir, "process_status_component_privilege=0.0.0"))

    # Then I can check the cli to see the status of component process_status_component_privilege is FINISHED
    timeout = 10
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                "process_status_component_privilege") == "FINISHED":
            break
        time.sleep(1)
        timeout -= 1

    # And I get assertions that the process was running as privileged user
    time.sleep(5)    #wait for process to finish
    assert (system_interface.check_systemd_user(
        "process_status_component_privilege", 15) == "User=root\n")
