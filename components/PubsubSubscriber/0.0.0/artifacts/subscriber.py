import time
from sys import argv, stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import BinaryMessage, PublishMessage, SubscriptionResponseMessage


def _on_stream_event(event: SubscriptionResponseMessage) -> None:
    try:
        message = str(event.binary_message.message, "utf-8")
        print("Received new message: %s" % message)
    except Exception as e:
        print("Exception occurred: " + str(e))
        print_exc()


def _on_stream_error(error: Exception) -> bool:
    print("Received a stream error.", file=stderr)
    print_exc()
    return False    # Return True to close stream, False to keep stream open.


def _on_stream_closed() -> None:
    print("Subscribe to topic stream closed.")


def subscribe_to_topic(ipc_client: GreengrassCoreIPCClientV2, topic):
    return ipc_client.subscribe_to_topic(topic=topic,
                                         on_stream_event=_on_stream_event,
                                         on_stream_error=_on_stream_error,
                                         on_stream_closed=_on_stream_closed)


def main():
    args = argv[1:]
    topic = args[0]
    message = " ".join(args[1:])

    try:
        ipc_client = GreengrassCoreIPCClientV2()
        # Subscribe to the topic before publishing
        _, operation = subscribe_to_topic(ipc_client, topic)
        print("Subscribed to %s topic" % topic)

        # Keep the main thread alive, or the process will exit
        try:
            while True:
                time.sleep(10)
        except InterruptedError:
            print("Subscribe interrupted")

        # Stop subscribing and close the stream
        operation.close()

    except Exception:
        print("Exception occurred", file=stderr)
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
