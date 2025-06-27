from time import sleep, time
from typing import List, Optional

import argparse
import botocore
import subprocess
import uuid
import json
from boto3 import client

JSON_FILE = "iot_setup_data.json"


def set_up_core_device(region: str):
    id = _set_random_id()
    thing_name = "ggl-uat-thing-" + id
    thing_group_name = "ggl-uat-thing-group-" + id

    iot_client = client("iot", region_name=region)
    iam_client = client("iam", region_name=region)

    device_cert, private_key = create_new_thing(thing_name, iot_client,
                                                iam_client)

    create_new_thing_group(thing_group_name, iot_client)
    add_thing_to_thing_group(thing_name, thing_group_name, iot_client)

    data = {
        "DEVICE_CERT": device_cert,
        "PRIVATE_KEY": private_key,
        "THING_NAME": thing_name,
        "THING_GROUP_NAME": thing_group_name
    }

    with open(JSON_FILE, 'w') as f:
        json.dump(data, f)


def create_new_thing(thing_name: str, iot_client: client,
                     iam_client: client) -> list | None:

    try:
        thing_response = iot_client.create_thing(thingName=thing_name)

        cert_response = iot_client.create_keys_and_certificate(setAsActive=True)

        cert_arn = cert_response['certificateArn']

        iot_client.attach_thing_principal(thingName=thing_name,
                                          principal=cert_arn)

    except Exception as error:
        print(f"Error when creating thing: {str(error)}")
        return None

    # set up role and role alias
    role_arn = _create_iot_role(iam_client)
    role_alias_arn = _create_role_alias(role_arn, iot_client)
    _attach_thing_policy(role_alias_arn, cert_arn, iot_client)

    print(f"Successfully created a thing: {thing_name}")
    return [
        cert_response['certificatePem'], cert_response['keyPair']['PrivateKey']
    ]


def create_new_thing_group(thing_group_name: str, iot_client: client) -> bool:
    response = iot_client.create_thing_group(thingGroupName=thing_group_name)
    if response is not None:
        print(f"Successfully created a thing group: {thing_group_name}")
        return True

    return False


def add_thing_to_thing_group(thing_name: str, thing_group_name: str,
                             iot_client: client) -> bool:
    response = iot_client.add_thing_to_thing_group(
        thingName=thing_name, thingGroupName=thing_group_name)

    if response['ResponseMetadata']['HTTPStatusCode'] == 200:
        print(
            f"Successfully added thing '{thing_name}' to thing group '{thing_group_name}'"
        )
        return True
    return False


def remove_all_things_from_thing_group(thing_group_name: str,
                                       iot_client: client,
                                       gg_client: client) -> bool | None:
    try:
        iot_client.describe_thing_group(thingGroupName=thing_group_name)
    except iot_client.exceptions.ResourceNotFoundException:
        print(f"Thing group {thing_group_name} does not exist")
        return None

    response = iot_client.list_things_in_thing_group(
        thingGroupName=thing_group_name, recursive=True)

    for thing in response['things']:
        iot_client.remove_thing_from_thing_group(
            thingGroupName=thing_group_name, thingName=thing)

        # Delete the corresponding thing
        thing_result = delete_thing(thing, iot_client)
        if not thing_result:
            return False

        # Delete the corresponding core device
        core_device_result = delete_core_device(thing, iot_client, gg_client)
        if not core_device_result:
            return False

    print(f"Removed all things from group {thing_group_name}")
    return True


def delete_core_device(thing_name: str, iot_client: client, gg_client: client):
    # Cancel all deployments for the core device
    try:
        deployments = gg_client.list_effective_deployments(
            coreDeviceThingName=thing_name)

        if not deployments.get('effectiveDeployments'):
            print("No deployments found to cancel")
        else:
            for deployment in deployments['effectiveDeployments']:
                gg_client.cancel_deployment(
                    deploymentId=deployment['deploymentId'])
            print(f"Cancelled all deployments")

    except Exception as e:
        print(f"Error when listing deployments: {str(e)}")
        return False

    # Delete the core device
    try:
        gg_client.delete_core_device(coreDeviceThingName=thing_name)
        print(f"Deleted Greengrass core device '{thing_name}'")
    except Exception as e:
        print(f"Failed to delete Greengrass core device: {str(e)}")
        return False

    return True


def delete_thing(thing_name: str, iot_client: client):
    try:
        # Get certificates that attached to the thing
        principals = iot_client.list_thing_principals(
            thingName=thing_name)['principals']

        # For each certificate attached to the thing
        for principal in principals:
            cert_id = principal.split('/')[-1]

            # Detach all policies from the certificate
            policies = iot_client.list_attached_policies(
                target=principal)['policies']

            for policy in policies:
                iot_client.detach_policy(policyName=policy['policyName'],
                                         target=principal)

            # Detach certificate from thing
            iot_client.detach_thing_principal(thingName=thing_name,
                                              principal=principal)

            # Update certificate to INACTIVE
            iot_client.update_certificate(certificateId=cert_id,
                                          newStatus='INACTIVE')

            # Delete the certificate
            iot_client.delete_certificate(certificateId=cert_id,
                                          forceDelete=True)

        # Finally, delete the thing
        iot_client.delete_thing(thingName=thing_name)

        print(
            f"Successfully deleted thing '{thing_name}' and its associated certificates"
        )
        return True

    except ClientError as e:
        error_code = e.response['Error']['Code']
        error_message = e.response['Error']['Message']
        print(
            f"Error deleting thing '{thing_name}': {error_code} - {error_message}"
        )
        return False
    except Exception as e:
        print(f"Unexpected error deleting thing '{thing_name}': {str(e)}")
        return False


def clean_up(region: str, thing_group_name: str):
    iot_client = client("iot", region_name=region)
    gg_client = client("greengrassv2", region_name=region)
    remove_all_things_from_thing_group(thing_group_name, iot_client, gg_client)
    iot_client.delete_thing_group(thingGroupName=thing_group_name)

    try:
        subprocess.run(['sudo', 'rm', '-rf', JSON_FILE], check=True)
    except Exception as e:
        print(f"Error when removing the JSON file, {str(e)}")

    print("Successfully cleaned up thing groups and their things")


# ===============================================
# HELPER FUNCTIONS
# ===============================================
def _set_random_id() -> str:
    return str(uuid.uuid4().hex)


def _create_iot_role(iam_client: client) -> str | None:
    trust_policy = {
        "Version":
        "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Principal": {
                "Service": "credentials.iot.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }]
    }

    token_exchange_policy = {
        "Version":
        "2012-10-17",
        "Statement": [{
            "Effect":
            "Allow",
            "Action": [
                "logs:CreateLogGroup", "logs:CreateLogStream",
                "logs:PutLogEvents", "logs:DescribeLogStreams", "s3:*"
            ],
            "Resource":
            "*"
        }]
    }

    role_name = "ggl-uat-role"
    policy_name = "ggl-uat-role-token-exchange-policy"

    try:
        role_response = iam_client.get_role(RoleName=role_name)
        print(f"Role '{role_name}' already exists.")
        return role_response['Role']['Arn']

    except iam_client.exceptions.NoSuchEntityException:
        role_response = iam_client.create_role(
            RoleName=role_name,
            AssumeRolePolicyDocument=json.dumps(trust_policy))

        policy_response = iam_client.create_policy(
            PolicyName=policy_name,
            PolicyDocument=json.dumps(token_exchange_policy))

        iam_client.attach_role_policy(
            RoleName=role_name, PolicyArn=policy_response['Policy']['Arn'])

        return role_response['Role']['Arn']

    except Exception as e:
        print(f"Error creating role: {str(e)}")
        return None


def _create_role_alias(role_arn: str, iot_client: client) -> str | None:
    role_alias_name = "ggl-uat-role-alias"

    try:
        response = iot_client.describe_role_alias(roleAlias=role_alias_name)
        print(f"Role alias '{role_alias_name}' already exists.")
        return response['roleAliasDescription']['roleAliasArn']

    except iot_client.exceptions.ResourceNotFoundException:
        response = iot_client.create_role_alias(roleAlias=role_alias_name,
                                                roleArn=role_arn,
                                                credentialDurationSeconds=3600)

        return response['roleAliasArn']

    except Exception as e:
        print(f"Error creating role alias: {str(e)}")
        return None


def _attach_thing_policy(role_alias_arn: str, cert_arn: str,
                         iot_client: client):
    iot_policy_name = "ggl-uat-thing-policy"

    policy_document = {
        "Version":
        "2012-10-17",
        "Statement": [{
            "Effect":
            "Allow",
            "Action": [
                "iot:Connect", "iot:Publish", "iot:Subscribe", "iot:Receive",
                "greengrass:*"
            ],
            "Resource":
            "*"
        }, {
            "Effect": "Allow",
            "Action": "iot:AssumeRoleWithCertificate",
            "Resource": f"{role_alias_arn}"
        }]
    }

    try:
        iot_client.get_policy(policyName=iot_policy_name)
        print(f"Policy '{iot_policy_name}' already exists.")

    except iot_client.exceptions.ResourceNotFoundException:
        iot_client.create_policy(policyName=iot_policy_name,
                                 policyDocument=json.dumps(policy_document))

    iot_client.attach_policy(policyName=iot_policy_name, target=cert_arn)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest='function',
                                       help='Function to help setup iot')
    # Parser for set up a core device
    source_parser = subparsers.add_parser('set_up_core_device')
    source_parser.add_argument('--region', required=True, help='AWS region')

    # Parser for clean_up
    cleanup_parser = subparsers.add_parser('clean_up')
    cleanup_parser.add_argument('--region', required=True, help='AWS region')
    cleanup_parser.add_argument('--thing_group',
                                required=True,
                                help='Thing group name')
    args = parser.parse_args()

    # Call the selected function with appropriate arguments
    if args.function == 'set_up_core_device':
        set_up_core_device(args.region)
    elif args.function == 'clean_up':
        clean_up(args.region, args.thing_group)
