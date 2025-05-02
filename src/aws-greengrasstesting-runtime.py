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
