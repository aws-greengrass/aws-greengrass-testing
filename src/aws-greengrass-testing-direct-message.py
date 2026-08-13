"""UATs for routing inbound AWS IoT Core messages to the subscribing component."""
import subprocess
import time
from typing import Generator, Tuple

from pytest import fixture

from src.GGTestUtils import GGTestUtils, ComponentDeploymentInfo, sleep_with_log
from src.IoTUtils import IoTUtils
from src.SystemInterface import SystemInterface
import src.GGLSetup as ggl_setup

IOTCORED_SERVICE = "ggl.core.iotcored.service"
COMPONENT = "DirectMessageReceiver"


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


# --- journal helpers -------------------------------------------------------

def _journal_lines(service: str) -> list:
    result = subprocess.run(
        ["sudo", "journalctl", "-u", service, "-b", "--no-pager"],
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout.splitlines()


def _journal_has(service: str, marker: str) -> bool:
    return any(marker in line for line in _journal_lines(service))


def _count_markers(service: str, marker: str) -> int:
    return sum(1 for line in _journal_lines(service) if marker in line)


def _wait_count(service: str, marker: str, minimum: int, timeout: int) -> int:
    """Poll until `marker` appears at least `minimum` times. Returns the count."""
    waited = 0
    while waited < timeout:
        count = _count_markers(service, marker)
        if count >= minimum:
            return count
        sleep_with_log(3)
        waited += 3
    return _count_markers(service, marker)


# --- deployment helpers ----------------------------------------------------

def _grant(topic_filter: str) -> dict:
    """accessControl granting SubscribeToIoTCore on a topic filter."""
    return {
        "accessControl": {
            "aws.greengrass.ipc.mqttproxy": {
                "DirectMessageReceiver:mqttproxy:1": {
                    "policyDescription": "receive IoT Core messages",
                    "operations": ["aws.greengrass#SubscribeToIoTCore"],
                    "resources": [topic_filter],
                }
            }
        }
    }


def _config(topic: str = None,
            mode: str = None,
            cloud_topic: str = None,
            grant_filter: str = None) -> dict:
    """Build the merge config; fields left None keep the recipe default,
    grant_filter adds an accessControl policy."""
    merge = {}
    if topic is not None:
        merge["Topic"] = topic
    if mode is not None:
        merge["SubscriptionMode"] = mode
    if cloud_topic is not None:
        merge["CloudSubscriptionTopic"] = cloud_topic
    if grant_filter is not None:
        merge.update(_grant(grant_filter))
    return merge


def _deploy(iot_obj: IoTUtils,
            gg_util_obj: GGTestUtils,
            merge_config: dict,
            wait_success: bool = True) -> Tuple[str, str]:
    """Cloud-deploy DirectMessageReceiver with the given merge config; returns
    the randomized (component, service) names. wait_success=False skips the
    success check for failure-path tests."""
    thing_group_name = iot_obj.generate_thing_group_name(
        iot_obj.generate_random_id())
    assert iot_obj.add_thing_to_thing_group(iot_obj.thing_name,
                                            thing_group_name) is True
    thing_group_arn = gg_util_obj.get_thing_group_arn(thing_group_name)

    component = gg_util_obj.upload_component_with_versions(
        COMPONENT, ["1.0.0"]).name
    service = f"ggl.{component}.service"
    sleep_with_log(5, "let cloud mark the component DEPLOYABLE")

    info = ComponentDeploymentInfo(name=component,
                                   versions=["1.0.0"],
                                   merge_config=merge_config)
    deployment_id = gg_util_obj.create_deployment(
        thing_group_arn, [info], "DirectMessageDeployment")["deploymentId"]
    assert deployment_id is not None
    if wait_success:
        assert gg_util_obj.wait_for_deployment_till_timeout(
            300, deployment_id) == "SUCCEEDED"
    return component, service


# --- tests -----------------------------------------------------------------

def test_DirectMessage_1_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A direct message on the registered filter reaches the RECEIVE_ONLY
    subscriber."""
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/#", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service, "Registered RECEIVE_ONLY subscription on topic cmd/#",
        timeout=120)

    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/light/on",
                                       "lights-on") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/light/on with payload: lights-on",
        timeout=60,
        since=since)


def test_DirectMessage_1_T2(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A RECEIVE_ONLY registration creates no cloud subscription: a normal
    broker publish is NOT received, but a direct message on the same topic
    is."""
    topic = f"cmd/{iot_obj.generate_random_id()}"
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/#", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service, "Registered RECEIVE_ONLY subscription on topic cmd/#",
        timeout=120)

    iot_obj.publish_to_iot_core(topic, "cloud-publish")
    sleep_with_log(20, "confirm the broker publish is not delivered")
    assert not _journal_has(service, "cloud-publish")

    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, topic,
                                       "direct-still-works") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        f"Received direct message on topic {topic} with payload: direct-still-works",
        timeout=60,
        since=since)


def test_DirectMessage_1_T3(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A component with no SubscribeToIoTCore grant cannot register, and a
    direct message it would have received is dropped and logged."""
    # Defaults (RECEIVE_ONLY on cmd/#), no accessControl grant.
    _, service = _deploy(iot_obj, gg_util_obj, _config(), wait_success=False)
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Registration failed on topic cmd/# with mode RECEIVE_ONLY "
        "- UnauthorizedError: IPC Operation not authorized.",
        timeout=120)

    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/light/on",
                                       "ungranted") == 200
    assert system_interface.monitor_journalctl_for_message(
        IOTCORED_SERVICE,
        "Dropping message on topic cmd/light/on: no matching subscription",
        timeout=60,
        since=since)
    assert not _journal_has(service, "ungranted")


def test_DirectMessage_1_T4(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A component that registered one specific topic does not receive another
    topic's direct messages, but does receive its own."""
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/status", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service, "Registered RECEIVE_ONLY subscription on topic cmd/status",
        timeout=120)

    # A different topic is not delivered.
    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/other",
                                       "other-topic") == 200
    assert system_interface.monitor_journalctl_for_message(
        IOTCORED_SERVICE,
        "Dropping message on topic cmd/other: no matching subscription",
        timeout=60,
        since=since)
    sleep_with_log(10, "quiet period")
    assert not _journal_has(service, "other-topic")

    # Its own topic is delivered.
    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/status",
                                       "own-topic") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/status with payload: own-topic",
        timeout=60,
        since=since)


def test_DirectMessage_1_T5(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """My registration is tied to my IPC stream and is re-created
    automatically when my component restarts"""
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/#", grant_filter="cmd/#"))
    marker = "Registered RECEIVE_ONLY subscription on topic cmd/#"
    assert system_interface.monitor_journalctl_for_message(
        service, marker, timeout=120)

    # Restart just the component; its stream closes and reopens.
    subprocess.run(["sudo", "systemctl", "restart", service], check=True)
    assert _wait_count(service, marker, minimum=2, timeout=120) == 2, (
        "Component did not re-register exactly once after restart")

    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/light/on",
                                       "after-restart") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/light/on with payload: after-restart",
        timeout=60,
        since=since)
    sleep_with_log(10, "quiet period to catch a duplicate delivery")
    assert _count_markers(
        service,
        "Received direct message on topic cmd/light/on with payload: after-restart"
    ) == 1


def test_DirectMessage_1_T6(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """I keep receiving direct messages after a nucleus restart: the component
    re-registers on startup and delivery resumes."""
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/#", grant_filter="cmd/#"))
    marker = "Registered RECEIVE_ONLY subscription on topic cmd/#"
    assert system_interface.monitor_journalctl_for_message(
        service, marker, timeout=120)

    assert system_interface.restart_systemd_nucleus_lite(60) is True
    # Wait for re-registration so the send can't race the restart.
    assert _wait_count(service, marker, minimum=2, timeout=180) == 2, (
        "Component did not re-register after the nucleus restart")

    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/light/on",
                                       "after-nucleus-restart") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/light/on with payload: "
        "after-nucleus-restart",
        timeout=60,
        since=since)


def test_DirectMessage_1_T7(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A subscription with no mode defaults to a cloud subscription (backward
    compatibility)."""
    topic = f"cmd/{iot_obj.generate_random_id()}"
    # Empty SubscriptionMode: no mode sent.
    _, service = _deploy(
        iot_obj, gg_util_obj,
        _config(topic=topic, mode="", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service,
        f"Registered cloud subscription on topic {topic} with no subscription mode",
        timeout=120)

    since = time.time()
    iot_obj.publish_to_iot_core(topic, "legacy-cloud-publish")
    assert system_interface.monitor_journalctl_for_message(
        service,
        f"Received direct message on topic {topic} with payload: legacy-cloud-publish",
        timeout=60,
        since=since)


def test_DirectMessage_1_T8(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """A component that asks for SUBSCRIBE_AND_RECEIVE gets a real cloud
    subscription."""
    topic = f"cmd/{iot_obj.generate_random_id()}"
    _, service = _deploy(
        iot_obj, gg_util_obj,
        _config(topic=topic, mode="SUBSCRIBE_AND_RECEIVE", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service,
        f"Registered cloud subscription on topic {topic} with mode SUBSCRIBE_AND_RECEIVE",
        timeout=120)

    since = time.time()
    iot_obj.publish_to_iot_core(topic, "explicit-cloud-publish")
    assert system_interface.monitor_journalctl_for_message(
        service,
        f"Received direct message on topic {topic} with payload: explicit-cloud-publish",
        timeout=60,
        since=since)


def test_DirectMessage_1_T9(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                            system_interface: SystemInterface):
    """An unrecognized subscription mode is rejected instead of silently
    creating a cloud subscription."""
    _, service = _deploy(iot_obj, gg_util_obj,
                         _config(topic="cmd/#", mode="FUTURE_MODE",
                                 grant_filter="cmd/#"),
                         wait_success=False)
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Registration failed on topic cmd/# with mode FUTURE_MODE "
        "- InvalidArgumentsError: 'subscriptionMode' not a valid value.",
        timeout=120)
    assert not _journal_has(service, "Registered cloud subscription")
    assert not _journal_has(service, "Registered RECEIVE_ONLY subscription")


def test_DirectMessage_1_T10(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                             system_interface: SystemInterface):
    """A cloud subscription and a covering local subscription each deliver
    their own copy: a message matching both is delivered twice, one matching
    only the local subscription once."""
    _, service = _deploy(
        iot_obj, gg_util_obj,
        _config(topic="cmd/#", cloud_topic="cmd/light/on", grant_filter="cmd/#"))
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Registered cloud subscription on topic cmd/light/on with mode "
        "SUBSCRIBE_AND_RECEIVE",
        timeout=120)
    assert system_interface.monitor_journalctl_for_message(
        service, "Registered RECEIVE_ONLY subscription on topic cmd/#",
        timeout=120)

    # Matches both registrations -> two deliveries, one per path.
    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/light/on",
                                       "double-delivery") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/light/on with payload: "
        "double-delivery via cloud",
        timeout=60,
        since=since)
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/light/on with payload: "
        "double-delivery via receive-only",
        timeout=60,
        since=since)

    # Matches only the receive-only filter -> a single delivery.
    since = time.time()
    assert iot_obj.send_direct_message(iot_obj.thing_name, "cmd/fan/off",
                                       "single-delivery") == 200
    assert system_interface.monitor_journalctl_for_message(
        service,
        "Received direct message on topic cmd/fan/off with payload: "
        "single-delivery via receive-only",
        timeout=60,
        since=since)
    sleep_with_log(10, "quiet period")
    assert _count_markers(service, "with payload: single-delivery") == 1
