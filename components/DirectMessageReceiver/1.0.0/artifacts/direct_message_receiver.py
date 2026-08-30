"""Test-only receiver for the direct-message UATs. Reads Topic,
SubscriptionMode, and CloudSubscriptionTopic (positional args from the recipe),
registers the matching SubscribeToIoTCore subscription(s) over IPC, and prints
one marker per registration outcome and per received message."""
import time
from sys import argv, stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import IoTCoreMessage, QOS

RECEIVE_ONLY = "RECEIVE_ONLY"
CLOUD_MODE = "SUBSCRIBE_AND_RECEIVE"


def _make_on_event(path: str):
    """Stream handler tagging each delivery with its path (cloud or
    receive-only). Logs the message's own topic, not the filter."""

    def _on_event(event: IoTCoreMessage) -> None:
        try:
            message = event.message
            payload = message.payload.decode(
                "utf-8") if message.payload else ""
            print(
                f"Received direct message on topic {message.topic_name} "
                f"with payload: {payload} via {path}",
                flush=True)
        except Exception:
            print_exc()

    return _on_event


def _on_error(error: Exception) -> bool:
    print("Subscription stream error.", file=stderr)
    print_exc()
    return False    # keep the stream open


def _on_closed() -> None:
    print("Subscription stream closed.", flush=True)


def _subscribe(ipc: GreengrassCoreIPCClientV2, topic: str, mode: str):
    """Register one subscription and log the outcome. Returns the streaming
    operation on success (kept alive by the caller) or None on failure. An empty
    mode omits subscriptionMode (legacy path)."""
    path = "receive-only" if mode == RECEIVE_ONLY else "cloud"
    kwargs = dict(topic_name=topic,
                  qos=QOS.AT_LEAST_ONCE,
                  on_stream_event=_make_on_event(path),
                  on_stream_error=_on_error,
                  on_stream_closed=_on_closed)
    # subscription_mode ships in the awsiotsdk fork (not yet released upstream).
    if mode:
        kwargs["subscription_mode"] = mode

    try:
        _, operation = ipc.subscribe_to_iot_core(**kwargs)
    except Exception as err:
        message = getattr(err, "message", None) or str(err)
        print(
            f"Registration failed on topic {topic} with mode {mode} "
            f"- {type(err).__name__}: {message}",
            flush=True)
        return None

    if mode == RECEIVE_ONLY:
        print(f"Registered RECEIVE_ONLY subscription on topic {topic}",
              flush=True)
    elif mode:
        print(
            f"Registered cloud subscription on topic {topic} with mode {mode}",
            flush=True)
    else:
        print(
            f"Registered cloud subscription on topic {topic} "
            f"with no subscription mode",
            flush=True)
    return operation


def main() -> None:
    topic = argv[1] if len(argv) > 1 else "cmd/#"
    mode = argv[2] if len(argv) > 2 else RECEIVE_ONLY
    cloud_topic = argv[3] if len(argv) > 3 else ""

    try:
        ipc = GreengrassCoreIPCClientV2()
        operations = []

        primary = _subscribe(ipc, topic, mode)
        if primary is None:
            # Marker already logged; exit non-zero so the deployment fails.
            exit(1)
        operations.append(primary)

        # Non-empty CloudSubscriptionTopic adds a second, always-cloud
        # subscription so a matching message is delivered via both paths.
        if cloud_topic:
            cloud = _subscribe(ipc, cloud_topic, CLOUD_MODE)
            if cloud is None:
                exit(1)
            operations.append(cloud)

        while True:
            time.sleep(10)
    except SystemExit:
        raise
    except Exception:
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
