from sys import argv, stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import BinaryMessage, PublishMessage, SubscriptionResponseMessage


def subscribe_to_topic(ipc_client: GreengrassCoreIPCClientV2, topic):
    print("Successfully subscribed to %s" % topic)
    return ipc_client.subscribe_to_topic(topic=topic,
                                         on_stream_event=_on_stream_event,
                                         on_stream_error=_on_stream_error,
                                         on_stream_closed=_on_stream_closed)


def _on_stream_event(event: SubscriptionResponseMessage) -> None:
    try:
        message = str(event.binary_message.message, "utf-8")
        topic = event.binary_message.context.topic
        print("Received new message on topic %s: %s" % (topic, message))
    except Exception as e:
        print("Exception occurred: " + str(e))
        print_exc()


def _on_stream_error(error: Exception) -> bool:
    print("Received a stream error.", file=stderr)
    print_exc()
    return False    # Return True to close stream, False to keep stream open.


def _on_stream_closed() -> None:
    print("Subscribe to topic stream closed.")


def publish_binary_message_to_topic(ipc_client: GreengrassCoreIPCClientV2,
                                    topic, message):
    print("Successfully published to %s" % topic)
    binary_message = BinaryMessage(message=bytes(message, "utf-8"))
    publish_message = PublishMessage(binary_message=binary_message)
    return ipc_client.publish_to_topic(topic=topic,
                                       publish_message=publish_message)


def publish_message_N_times(ipc_client, topic, message, N=10):
    for i in range(1, N + 1):
        publish_binary_message_to_topic(ipc_client, topic, message)
        print("Successfully published " + str(i) + " message(s)")


def main():
    args = argv[1:]
    topic = args[0]
    message = " ".join(args[1:])

    try:
        ipc_client = GreengrassCoreIPCClientV2()
        # Subscribe to the topic before publishing
        subscribe_to_topic(ipc_client, topic)
        # Publish a message for N times and exit
        publish_message_N_times(ipc_client, topic, message)
    except Exception:
        print("Exception occurred", file=stderr)
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
