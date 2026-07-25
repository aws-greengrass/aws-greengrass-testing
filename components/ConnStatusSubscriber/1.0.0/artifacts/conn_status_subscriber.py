import time
from sys import stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import IoTCoreConnectionStatusEvent


def _on_event(event: IoTCoreConnectionStatusEvent) -> None:
    try:
        status = event.connection_status_event.status
        # Unique marker the UAT greps for.
        print(f"CONN_STATUS_RECEIVED status={status}", flush=True)
    except Exception:
        print_exc()


def _on_error(error: Exception) -> bool:
    print("Connection status stream error.", file=stderr)
    print_exc()
    return False


def _on_closed() -> None:
    print("Connection status stream closed.", flush=True)


def main() -> None:
    try:
        ipc = GreengrassCoreIPCClientV2()
        # No accessControl policy is configured in the recipe: this operation
        # is a local informational subscription only
        _, op = ipc.subscribe_to_iot_core_connection_status(
            on_stream_event=_on_event,
            on_stream_error=_on_error,
            on_stream_closed=_on_closed,
        )
        print("CONN_STATUS_SUBSCRIBED", flush=True)
        while True:
            time.sleep(10)
    except Exception:
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
