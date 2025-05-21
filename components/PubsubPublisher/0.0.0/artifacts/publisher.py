from sys import argv, stderr
from traceback import print_exc

from awsiot.greengrasscoreipc.clientv2 import GreengrassCoreIPCClientV2
from awsiot.greengrasscoreipc.model import BinaryMessage, PublishMessage, SubscriptionResponseMessage, UnauthorizedError


def publish_binary_message_to_topic(ipc_client: GreengrassCoreIPCClientV2,
                                    topic, message):
    binary_message = BinaryMessage(message=bytes(message, "utf-8"))
    publish_message = PublishMessage(binary_message=binary_message)
    return ipc_client.publish_to_topic(topic=topic,
                                       publish_message=publish_message)


def publish_message_N_times(ipc_client, topic, message, N=10):
    for i in range(1, N + 1):
        publish_binary_message_to_topic(ipc_client, topic, message)


def main():
    args = argv[1:]
    topic = args[0]
    message = " ".join(args[1:])

    try:
        ipc_client = GreengrassCoreIPCClientV2()
        # Publish a message for N times and exit
        publish_message_N_times(ipc_client, topic, message)
        print("Published to %s topic" % topic)
    except UnauthorizedError:
        print('Unauthorized error while publishing to topic: %s' % topic,
              file=stderr)
        print_exc()
        exit(1)
    except Exception:
        print("Exception occurred", file=stderr)
        print_exc()
        exit(1)


if __name__ == "__main__":
    main()
