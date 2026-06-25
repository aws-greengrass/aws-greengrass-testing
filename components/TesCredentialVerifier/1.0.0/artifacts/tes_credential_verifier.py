"""TES Credential Verifier — loops calling sts:GetCallerIdentity and
iot:DescribeEndpoint, printing results for UAT journalctl assertions."""

import time
import boto3


def main():
    while True:
        try:
            sts = boto3.client("sts")
            identity = sts.get_caller_identity()
            arn = identity["Arn"]
            print(f"TES_ROLE_ARN={arn}", flush=True)
        except Exception as e:
            print(f"TES_ROLE_ARN=ERROR: {e}", flush=True)

        try:
            iot = boto3.client("iot")
            resp = iot.describe_endpoint(endpointType="iot:Data-ATS")
            endpoint = resp["endpointAddress"]
            print(f"IOT_ENDPOINT={endpoint}", flush=True)
        except Exception as e:
            print(f"IOT_ENDPOINT=ERROR: {e}", flush=True)

        time.sleep(5)


if __name__ == "__main__":
    main()
