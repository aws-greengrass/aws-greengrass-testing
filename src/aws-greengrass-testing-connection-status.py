"""UATs for the SubscribeToIoTCoreConnectionStatus IPC operation.

Both tests deploy ConnStatusSubscriber (v1.0.0, built on awsiotsdk 1.31.0)
via a CLOUD deployment targeting a thing group, and read the component's
journal markers to observe the stream events it received.

test_ConnectionStatus_1_T1 — positive path:
  1. The subscribe call succeeds with NO accessControl policy in the recipe
  2. The current status (CONNECTED, since the core just completed a cloud
     deployment) is delivered as the first stream event.
  3. Exactly one initial event is delivered — no duplicate or spurious
     transition events arrive while the connection is stable.

test_ConnectionStatus_1_T2 — negative path:
  4. With the device certificate deactivated, a restarted core cannot
     connect, so the resubscribing component's initial status event is
     DISCONNECTED; once the certificate is reactivated the core reconnects
     and the component receives a CONNECTED event.
"""
import subprocess
from typing import Generator, Tuple

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


def _event_sequence(service: str) -> list:
    """Return the component's markers in journal order as a list of tokens:
    "SUBSCRIBED" for each subscribe, and "CONNECTED"/"DISCONNECTED" for each
    status event. Reading the journal in order (rather than counting) lets
    assertions anchor on the most recent subscribe, so events from a previous
    instance's shutdown can never satisfy them."""
    result = subprocess.run(
        ["sudo", "journalctl", "-u", service, "-b", "--no-pager"],
        capture_output=True,
        text=True,
        check=False,
    )
    sequence = []
    for line in result.stdout.splitlines():
        if "CONN_STATUS_SUBSCRIBED" in line:
            sequence.append("SUBSCRIBED")
        elif "CONN_STATUS_RECEIVED status=CONNECTED" in line:
            sequence.append("CONNECTED")
        elif "CONN_STATUS_RECEIVED status=DISCONNECTED" in line:
            sequence.append("DISCONNECTED")
    return sequence


def _events_after_nth_subscribe(service: str, n: int) -> list:
    """Status events that appear after the n-th (1-based) subscribe. Returns
    None while fewer than n subscribes have been logged."""
    sequence = _event_sequence(service)
    subscribes = [i for i, tok in enumerate(sequence) if tok == "SUBSCRIBED"]
    if len(subscribes) < n:
        return None
    return [
        tok for tok in sequence[subscribes[n - 1] + 1:] if tok != "SUBSCRIBED"
    ]


def _wait_events_after_nth_subscribe(service: str,
                                     n: int,
                                     minimum: int,
                                     timeout: int,
                                     poll_interval: int = 5) -> list:
    """Poll until at least `minimum` status events follow the n-th subscribe.
    Returns the events observed (possibly fewer than `minimum` on timeout)."""
    waited = 0
    events = _events_after_nth_subscribe(service, n)
    while (events is None or len(events) < minimum) and waited < timeout:
        sleep_with_log(poll_interval)
        waited += poll_interval
        events = _events_after_nth_subscribe(service, n)
    return events or []


def _wait_token_after_nth_subscribe(service: str,
                                    n: int,
                                    token: str,
                                    timeout: int,
                                    poll_interval: int = 5) -> list:
    """Poll until `token` appears among the status events following the n-th
    subscribe. Waiting on the token itself (rather than on an event count) is
    required because a failed connect emits more than one DISCONNECTED — the
    subscribe-accept status plus the connect-failure teardown — so a count
    threshold can be met without the awaited transition ever occurring.
    Returns the events observed."""
    waited = 0
    events = _events_after_nth_subscribe(service, n) or []
    while token not in events and waited < timeout:
        sleep_with_log(poll_interval)
        waited += poll_interval
        events = _events_after_nth_subscribe(service, n) or []
    return events


def _wait_running(system_interface: SystemInterface,
                  component: str,
                  timeout: int = 180):
    while timeout > 0:
        if system_interface.check_systemctl_status_for_component(
                component) == "RUNNING":
            return
        sleep_with_log(1)
        timeout -= 1


def _deploy_subscriber_and_wait_connected(
        iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
        system_interface: SystemInterface) -> Tuple[str, str]:
    """Cloud-deploy ConnStatusSubscriber to the core device and wait until it
    is subscribed and has received the initial CONNECTED event. Returns the
    randomized (component, service) names."""
    # Add the core device's thing to a new thing group so we can target it
    # via a cloud deployment.
    thing_group_name = iot_obj.generate_thing_group_name(
        iot_obj.generate_random_id())
    assert iot_obj.add_thing_to_thing_group(iot_obj.thing_name,
                                            thing_group_name) is True
    thing_group_arn = gg_util_obj.get_thing_group_arn(thing_group_name)

    # Upload the component. The framework registers it in the cloud under a
    # randomized name (e.g. "ConnStatusSubscriber<uuid>"), so reuse that
    # returned name for the deployment and on-device (systemd) lookup.
    component = gg_util_obj.upload_component_with_versions(
        "ConnStatusSubscriber", ["1.0.0"]).name
    service = f"ggl.{component}.service"
    sleep_with_log(5, "let cloud mark the component DEPLOYABLE")

    info = ComponentDeploymentInfo(name=component,
                                   versions=["1.0.0"],
                                   merge_config={})
    deployment_id = gg_util_obj.create_deployment(
        thing_group_arn, [info], "ConnStatusDeployment")["deploymentId"]
    assert deployment_id is not None
    assert gg_util_obj.wait_for_deployment_till_timeout(
        240, deployment_id) == "SUCCEEDED"
    _wait_running(system_interface, component)

    # The subscribe call must succeed even though the recipe carries NO
    # accessControl policy for this operation.
    assert system_interface.monitor_journalctl_for_message(
        service, "CONN_STATUS_SUBSCRIBED", timeout=60) is True

    # The core just completed a cloud deployment, so it is connected; the
    # current status must arrive as the first stream event.
    assert system_interface.monitor_journalctl_for_message(
        service, "CONN_STATUS_RECEIVED status=CONNECTED", timeout=60) is True

    return component, service


def test_ConnectionStatus_1_T1(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                               system_interface: SystemInterface):
    """Positive path: the subscription is accepted with no accessControl
    policy, the current status (CONNECTED) arrives as the first stream event,
    and no duplicate or spurious events follow while the connection stays
    up."""
    marker = "CONN_STATUS_RECEIVED"
    _, service = _deploy_subscriber_and_wait_connected(iot_obj, gg_util_obj,
                                                       system_interface)

    # Give the stream a quiet period, then verify exactly one initial event
    # was delivered: no duplicates and no spurious transitions while the
    # connection is stable.
    sleep_with_log(15, "quiet period to catch duplicate or spurious events")

    connected_count = _count_markers(service, f"{marker} status=CONNECTED")
    disconnected_count = _count_markers(service,
                                        f"{marker} status=DISCONNECTED")
    print(f"CONNECTED events: {connected_count}, "
          f"DISCONNECTED events: {disconnected_count}")
    assert connected_count == 1, (
        f"Expected exactly one initial CONNECTED event, got "
        f"{connected_count}")
    assert disconnected_count == 0, (
        f"Expected no DISCONNECTED events while the connection is stable, "
        f"got {disconnected_count}")


def test_ConnectionStatus_1_T2(iot_obj: IoTUtils, gg_util_obj: GGTestUtils,
                               system_interface: SystemInterface):
    """Negative path, driven from the disconnected side.

    Deactivating a certificate does not tear down an already-established MQTT
    session (enforcement on live connections is lazy and a connected device
    only notices via a failed keepalive ping), so this test restarts the core
    while the certificate is INACTIVE. iotcored's connect attempt then fails
    and it stays in its backoff loop (mqtt.c: gg_backoff 5s->5m), so
    `iotcored_mqtt_connection_status()` reports false and the resubscribing
    component's INITIAL status event is DISCONNECTED (bus_server.c sends the
    current status on every subscribe accept). Reactivating the certificate
    lets the next backoff retry succeed, producing a CONNECTED event.

    Assertions anchor on the post-restart subscribe, so a DISCONNECTED
    emitted while the previous instance was shutting down cannot satisfy
    them."""
    _, service = _deploy_subscriber_and_wait_connected(iot_obj, gg_util_obj,
                                                       system_interface)

    # The pre-restart instance is subscribe #1; the restart produces #2.
    pre_restart = _event_sequence(service)
    print(f"Event sequence before restart: {pre_restart}")
    assert pre_restart.count("SUBSCRIBED") == 1, (
        f"Expected exactly one subscribe before the restart, got sequence "
        f"{pre_restart}")

    # Find the certificate(s) attached to the core device's thing (same
    # idiom IoTUtils.delete_thing uses).
    principals = iot_obj._iot_client.list_thing_principals(
        thingName=iot_obj.thing_name)['principals']
    assert principals, "No certificate attached to the core device's thing"
    cert_ids = [principal.split('/')[-1] for principal in principals]

    try:
        for cert_id in cert_ids:
            print(f"Deactivating device certificate {cert_id}")
            iot_obj._iot_client.update_certificate(certificateId=cert_id,
                                                   newStatus='INACTIVE')
        sleep_with_log(30, "let certificate deactivation propagate")

        assert system_interface.restart_systemd_nucleus_lite(60) is True
        _wait_running(system_interface,
                      service.removeprefix("ggl.").removesuffix(".service"))

        # First status event after the SECOND subscribe must be DISCONNECTED:
        # the restarted core cannot authenticate with an INACTIVE cert.
        events = _wait_events_after_nth_subscribe(service,
                                                  n=2,
                                                  minimum=1,
                                                  timeout=300)
        print(f"Events after post-restart subscribe: {events}")
        assert events, (
            "Subscriber logged no status event after resubscribing following "
            "the restart with a deactivated certificate")
        assert events[0] == "DISCONNECTED", (
            f"Expected the post-restart INITIAL status event to be "
            f"DISCONNECTED (core cannot connect with an INACTIVE "
            f"certificate), but the first event was {events[0]}; full "
            f"sequence after resubscribe: {events}")

        # Reactivate: the next backoff retry should connect and broadcast
        # CONNECTED. Retries start at 5s and back off to 5m, so allow a wide
        # window for reactivation to propagate and land on a retry.
        for cert_id in cert_ids:
            print(f"Reactivating device certificate {cert_id}")
            iot_obj._iot_client.update_certificate(certificateId=cert_id,
                                                   newStatus='ACTIVE')

        events = _wait_token_after_nth_subscribe(service,
                                                 n=2,
                                                 token="CONNECTED",
                                                 timeout=420)
        print(f"Events after reactivation: {events}")
        assert "CONNECTED" in events, (
            "Subscriber did not receive a CONNECTED transition within 7 "
            f"minutes of certificate reactivation; events after resubscribe: "
            f"{events}")
        # The reconnect must come after the disconnected period, never before.
        assert events.index("CONNECTED") > 0, (
            f"CONNECTED appeared before any DISCONNECTED after the restart; "
            f"sequence after resubscribe: {events}")
    finally:
        # Ensure the certificate is ACTIVE for teardown regardless of where
        # the test failed. The fixtures fully delete the thing and
        # certificate afterwards.
        for cert_id in cert_ids:
            try:
                iot_obj._iot_client.update_certificate(certificateId=cert_id,
                                                       newStatus='ACTIVE')
            except Exception as e:
                print(f"Could not reactivate certificate {cert_id}: {e}")
