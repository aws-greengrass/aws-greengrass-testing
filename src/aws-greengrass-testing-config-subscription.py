"""UAT: SubscribeToConfigurationUpdate must only fire on actual config value
changes, not on every deployment.

This test deploys ConfigUpdateSubscriber (v1.0.0) via a CLOUD deployment
(targeting a thing group) and verifies:

  1. Re-deploying the same recipe / same merge config does NOT produce any
     additional CONFIG_UPDATE_RECEIVED events.
  2. Deploying the same component version but with a DIFFERENT merged config
     value DOES produce a new CONFIG_UPDATE_RECEIVED event.
"""
import subprocess
from typing import Generator

from pytest import fixture

from src.GGTestUtils import GGTestUtils, ComponentDeploymentInfo, sleep_with_log
from src.IoTUtils import IoTUtils
from src.SystemInterface import SystemInterface
import src.GGLSetup as ggl_setup


@fixture(scope="function")
def gg_util_obj(request) -> Generator[GGTestUtils, None, None]:
    obj = GGTestUtils(request.config.getoption("--aws-account"),
                      request.config.getoption("--s3-bucket"),
                      request.config.getoption("--region"),
                      request.config.getoption("--ggl-cli-path"))
    yield obj
    obj.cleanup()


@fixture(scope="function")
def iot_obj(request) -> Generator[IoTUtils, None, None]:
    region = request.config.getoption("--region")
    commit_id = request.config.getoption("--commit-id")
    obj = IoTUtils(region)
    obj.set_up_core_device()
    ggl_setup.setup_greengrass_lite(commit_id, region)
    yield obj
    ggl_setup.clean_up()
    obj.clean_up()


@fixture(scope="function")
def system_interface() -> Generator[SystemInterface, None, None]:
    yield SystemInterface()


def _count_markers(service: str, marker: str) -> int:
    """Return total count of `marker` occurrences in `service`'s journal
    for the current boot."""
    result = subprocess.run(
        ["sudo", "journalctl", "-u", service, "-b", "--no-pager"],
        capture_output=True,
        text=True,
        check=False,
    )
    return sum(1 for line in result.stdout.splitlines() if marker in line)


def _wait_running(system_interface: SystemInterface,
                  component: str,
                  timeout: int = 180):
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                component) == "RUNNING":
            return
        sleep_with_log(1)
        timeout -= 1


def _deploy(gg_util_obj: GGTestUtils, thing_group_arn: str, component: str,
            version: str, merge_config: dict, name: str) -> str:
    info = ComponentDeploymentInfo(name=component,
                                   versions=[version],
                                   merge_config=merge_config)
    deployment_id = gg_util_obj.create_deployment(thing_group_arn, [info],
                                                  name)["deploymentId"]
    assert deployment_id is not None
    assert gg_util_obj.wait_for_deployment_till_timeout(
        240, deployment_id) == "SUCCEEDED"
    return deployment_id


def test_ConfigUpdateSubscription_no_duplicate_on_repeat_deploy(
        iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
        system_interface: SystemInterface):
    component = "ConfigUpdateSubscriber"
    version = "1.0.0"
    marker = "CONFIG_UPDATE_RECEIVED"

    # Add the core device's thing to a new thing group so we can target it
    # via a cloud deployment.
    thing_group_name = iot_obj.generate_thing_group_name(
        iot_obj.generate_random_id())
    assert iot_obj.add_thing_to_thing_group(iot_obj.thing_name,
                                            thing_group_name) is True
    thing_group_arn = gg_util_obj.get_thing_group_arn(thing_group_name)

    # Upload the component once. The framework registers it in the cloud under
    # a randomized name (e.g. "ConfigUpdateSubscriber<uuid>"), so reuse that
    # returned name for every deployment and on-device (systemd) lookup. All
    # subsequent deployments reuse this same version and vary only the merged
    # configuration.
    component = gg_util_obj.upload_component_with_versions(
        component, [version]).name
    service = f"ggl.{component}.service"
    sleep_with_log(5, "let cloud mark the component DEPLOYABLE")

    # First deployment: component starts and subscribes. The default config is
    # written once, so exactly one CONFIG_UPDATE_RECEIVED event is expected
    # (for the initial write).
    _deploy(gg_util_obj, thing_group_arn, component, version,
            {"watchedKey": "v1"}, "Deployment1")
    _wait_running(system_interface, component)

    # Wait for subscription to be established and initial event delivered.
    assert system_interface.monitor_journalctl_for_message(
        service, "CONFIG_UPDATE_SUBSCRIBED", timeout=60) is True
    sleep_with_log(5, "let initial config update event flush")

    baseline = _count_markers(service, marker)
    print(f"Baseline {marker} count: {baseline}")

    # Re-deploy with an identical merge config twice. Config values are
    # unchanged, so ggconfigd must suppress subscriber notifications even
    # though the deployment re-writes the same values.
    for i in range(2):
        _deploy(gg_util_obj, thing_group_arn, component, version,
                {"watchedKey": "v1"}, f"DeploymentRepeat{i + 1}")
        sleep_with_log(10, "let deployment propagate")

    after_redeploy = _count_markers(service, marker)
    print(f"{marker} after re-deploys: {after_redeploy}")
    assert after_redeploy == baseline, (
        f"Expected no new config update events after redeploying identical "
        f"config, but count went from {baseline} to {after_redeploy}")

    # Positive check: deploy the same version with a DIFFERENT merged value
    # for watchedKey. This MUST produce a new CONFIG_UPDATE_RECEIVED event.
    _deploy(gg_util_obj, thing_group_arn, component, version,
            {"watchedKey": "v2"}, "Deployment2")
    sleep_with_log(15, "let config-change deployment propagate")

    after_change = _count_markers(service, marker)
    print(f"{marker} after value change: {after_change}")
    assert after_change > after_redeploy, (
        f"Expected new config update event after value change, but count "
        f"stayed at {after_change} (was {after_redeploy})")
