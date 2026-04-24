import time
from sys import stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import ConfigurationUpdateEvents


def _on_event(event: ConfigurationUpdateEvents) -> None:
    try:
        e = event.configuration_update_event
        key_path = "/".join(e.key_path) if e.key_path else ""
        # Unique marker the UAT greps for.
        print(
            f"CONFIG_UPDATE_RECEIVED component={e.component_name} "
            f"keyPath={key_path}",
            flush=True)
    except Exception:
        print_exc()


def _on_error(error: Exception) -> bool:
    print("Config update stream error.", file=stderr)
    print_exc()
    return False


def _on_closed() -> None:
    print("Config update stream closed.", flush=True)


def main() -> None:
    try:
        ipc = GreengrassCoreIPCClientV2()
        _, op = ipc.subscribe_to_configuration_update(
            key_path=[],
            on_stream_event=_on_event,
            on_stream_error=_on_error,
            on_stream_closed=_on_closed,
        )
        print("CONFIG_UPDATE_SUBSCRIBED", flush=True)
        while True:
            time.sleep(10)
    except Exception:
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
