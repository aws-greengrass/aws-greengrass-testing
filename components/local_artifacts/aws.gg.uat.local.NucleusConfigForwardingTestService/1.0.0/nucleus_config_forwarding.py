# aws-greengrass-testing
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
"""Verifies Nucleus -> NucleusLite config forwarding over Greengrass IPC.

On Greengrass Lite, nucleus configuration is stored under the component name
``aws.greengrass.NucleusLite``. This component talks to the nucleus using the
classic ``aws.greengrass.Nucleus`` name and confirms:

1. GetConfiguration: a forwarded read returns the same value as a direct
   NucleusLite read, and the response echoes the requested name.
2. SubscribeToConfigurationUpdate: subscribing by the classic name is accepted
   (forwarded), and update events are delivered relabeled to
   ``aws.greengrass.Nucleus`` (the name the caller subscribed to), not the
   underlying ``aws.greengrass.NucleusLite``.

The component logs sentinel lines that the integration test asserts on. It
subscribes to the whole nucleus configuration and then blocks until it receives
an update event (driven by the test via a NucleusLite config merge) or times
out.
"""
import threading
import time
from sys import stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2

NUCLEUS = "aws.greengrass.Nucleus"
NUCLEUS_LITE = "aws.greengrass.NucleusLite"
# iotDataEndpoint is seeded under NucleusLite configuration by the test setup.
KEY = "iotDataEndpoint"
# Keep in sync with probe_key in test_Component_30_T0.
PROBE_KEY = "uatForwardingProbe"
# Max time to wait for the test to drive a config-update event.
EVENT_WAIT_SECONDS = 420
# How often sentinel lines are re-printed for late-attaching journal monitors.
REPRINT_INTERVAL_SECONDS = 5
# How long to keep re-printing sentinels after the update event arrives. This
# does not affect test runtime (the test finishes once its journal assertions
# match); it keeps the sentinel block near the journal tail so failures are
# easy to diagnose from captured logs, including monitors run without `since`.
POST_EVENT_LINGER_SECONDS = 120


def read_value(ipc_client, component_name, key):
    resp = ipc_client.get_configuration(component_name=component_name,
                                        key_path=[key])
    # For a non-map value, the response value is {key: value}.
    value = resp.value.get(key) if resp.value is not None else None
    return resp.component_name, value


def main():
    ipc_client = None
    try:
        ipc_client = GreengrassCoreIPCClientV2()

        # The test harness monitors the journal with `journalctl -f`, which
        # only replays a few recent lines. Sentinel lines are therefore
        # re-printed periodically so a monitor attaching at any time sees them.
        sentinel_lines = []
        sentinel_lock = threading.Lock()

        def emit(line):
            with sentinel_lock:
                sentinel_lines.append(line)
            print(line)

        def reprint_sentinels():
            with sentinel_lock:
                for line in sentinel_lines:
                    print(line)

        # --- GetConfiguration forwarding ---
        fwd_name, fwd_val = read_value(ipc_client, NUCLEUS, KEY)
        _, direct_val = read_value(ipc_client, NUCLEUS_LITE, KEY)

        emit("Forwarded Nucleus %s: %s" % (KEY, fwd_val))
        emit("Direct NucleusLite %s: %s" % (KEY, direct_val))
        emit("Echoed componentName: %s" % fwd_name)
        if fwd_name == NUCLEUS:
            emit("NUCLEUS RESPONSE COMPONENT NAME MATCH")
        else:
            emit("NUCLEUS RESPONSE COMPONENT NAME MISMATCH")

        if fwd_val is not None and fwd_val == direct_val:
            emit("NUCLEUS FORWARDING MATCH")
        else:
            emit("NUCLEUS FORWARDING MISMATCH")

        # --- SubscribeToConfigurationUpdate forwarding + relabel ---
        got_event = threading.Event()

        def on_event(event):
            try:
                update = event.configuration_update_event
                event_key_path = list(
                    update.key_path) if update.key_path else []
                key_path = "/".join(event_key_path)
                emit("CONFIG UPDATE EVENT componentName=%s keyPath=%s" %
                     (update.component_name, key_path))
                if event_key_path == [PROBE_KEY]:
                    got_event.set()
            except Exception as e:    # noqa: BLE001 - report and continue
                print("Config update event handler error: %s" % e)

        try:
            # Empty key_path subscribes to the whole component configuration, so
            # any sub-key write notifies us.
            ipc_client.subscribe_to_configuration_update(
                component_name=NUCLEUS,
                key_path=[],
                on_stream_event=on_event,
            )
            emit("NUCLEUS SUBSCRIBE OK")
        except Exception as e:    # noqa: BLE001 - report and exit broken
            print("NUCLEUS SUBSCRIBE FAILED: %s" % e)
            # Exit non-zero so the component reports broken rather than
            # appearing healthy; close() still runs via the finally below.
            exit(1)

        # Wait for the test to drive a NucleusLite config change (delivered to
        # this aws.greengrass.Nucleus subscriber), re-printing sentinels so the
        # test's journal monitors observe them regardless of attach time.
        deadline = time.monotonic() + EVENT_WAIT_SECONDS
        while time.monotonic() < deadline:
            if got_event.wait(timeout=REPRINT_INTERVAL_SECONDS):
                break
            reprint_sentinels()

        if not got_event.is_set():
            print("NUCLEUS SUBSCRIBE EVENT TIMEOUT")

        # Linger after the event so monitors that attach later still see the
        # full sentinel block (including the update event line).
        linger_deadline = time.monotonic() + POST_EVENT_LINGER_SECONDS
        while time.monotonic() < linger_deadline:
            time.sleep(REPRINT_INTERVAL_SECONDS)
            reprint_sentinels()
    except Exception:
        print("Exception occurred", file=stderr)
        print_exc()
        exit(1)
    finally:
        # Always release the IPC connection, including on failure paths
        # (exit() raises SystemExit, which still runs this).
        if ipc_client is not None:
            ipc_client.close()


if __name__ == "__main__":
    main()
