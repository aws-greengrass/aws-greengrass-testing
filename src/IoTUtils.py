from time import sleep, time
from typing import List, Optional
from boto3 import client
from types_boto3_iot import IoTClient
import botocore
import json
import subprocess
import uuid

JSON_FILE = "iot_setup_data.json"


class IoTUtils():
    _region: str
    _thing_name: str = None
    _thing_group_name: str = None

    def __init__(self, region: str):
        self._region = region
        self._iotClient = client("iot", region_name=self._region)
        self._iamClient = client("iam", region_name=self._region)
        self._ggClient = client("greengrassv2", region_name=self._region)

    @property
    def thing_name(self):
        return self._thing_name

    @property
    def thing_group_name(self):
        return self._thing_group_name

    def set_random_id(self):
        return str(uuid.uuid4().hex)

    def set_thing_name(self, id):
        return "ggl-uat-thing-" + id

    def set_thing_group_name(self, id):
        return "ggl-uat-thing-group-" + id

    def set_up_core_device(self):
        id = self.set_random_id()
        self._thing_name = self.set_thing_name(id)
        self._thing_group_name = self.set_thing_group_name(id)

        device_cert, private_key = self.create_new_thing(self._thing_name)

        #TODO: consider the single thing deployment
        self.add_thing_to_thing_group(self._thing_name, self._thing_group_name)

        data = {
            "DEVICE_CERT": device_cert,
            "PRIVATE_KEY": private_key,
            "THING_NAME": self._thing_name
        }

        with open(JSON_FILE, 'w') as f:
            json.dump(data, f)

    def create_new_thing(self, thing_name: str) -> list | None:
        try:
            thing_response = self._iotClient.create_thing(thingName=thing_name)

            cert_response = self._iotClient.create_keys_and_certificate(
                setAsActive=True)

            cert_arn = cert_response['certificateArn']

            self._iotClient.attach_thing_principal(thingName=thing_name,
                                                   principal=cert_arn)

        except Exception as error:
            print(f"Error when creating thing: {str(error)}")
            return None

        # set up role and role alias
        role_arn = self._create_iot_role()
        role_alias_arn = self._create_role_alias(role_arn)
        self._attach_thing_policy(role_alias_arn, cert_arn)

        print(f"Successfully created a thing: {thing_name}")
        return [
            cert_response['certificatePem'],
            cert_response['keyPair']['PrivateKey']
        ]

    def create_new_thing_group(self, thing_group_name: str) -> bool:
        response = self._iotClient.create_thing_group(
            thingGroupName=thing_group_name)
        if response is not None:
            print(f"Successfully created a thing group: {thing_group_name}")
            return True

        return False

    def add_thing_to_thing_group(self, thing_name: str,
                                 thing_group_name: str) -> bool:
        # Check if the thing group exists
        try:
            self._iotClient.describe_thing_group(
                thingGroupName=thing_group_name)
        except self._iotClient.exceptions.ResourceNotFoundException:
            # Thing group doesn't exist, create it
            self.create_new_thing_group(thing_group_name)

        response = self._iotClient.add_thing_to_thing_group(
            thingName=thing_name, thingGroupName=thing_group_name)

        if response['ResponseMetadata']['HTTPStatusCode'] == 200:
            print(
                f"Successfully added thing '{thing_name}' to thing group '{thing_group_name}'"
            )
            return True
        return False

    def remove_all_thing_groups_from_thing(self,
                                           thing_name: str) -> bool | None:
        try:
            response = self._iotClient.list_thing_groups_for_thing(
                thingName=thing_name)
        except self._iotClient.exceptions.ResourceNotFoundException:
            print(f"Thing {thing_name} does not exist")
            return None

        for thing_group in response['thingGroups']:
            self._iotClient.remove_thing_from_thing_group(
                thingGroupName=thing_group['groupName'], thingName=thing_name)

            # Delete the corresponding thing group
            thing_group_result = self.delete_thing_group(
                thing_group['groupName'])
            if not thing_group_result:
                return False

        print(f"Removed all thing groups from thing {thing_name}")
        return True

    def remove_all_things_from_thing_group(
            self, thing_group_name: str) -> bool | None:
        try:
            self._iotClient.describe_thing_group(
                thingGroupName=thing_group_name)
        except self._iotClient.exceptions.ResourceNotFoundException:
            print(f"Thing group {thing_group_name} does not exist")
            return None

        response = self._iotClient.list_things_in_thing_group(
            thingGroupName=thing_group_name, recursive=True)

        for thing in response['things']:
            self._iotClient.remove_thing_from_thing_group(
                thingGroupName=thing_group_name, thingName=thing)

            # Delete the corresponding thing
            thing_result = self.delete_thing(thing)
            if not thing_result:
                return False

        print(f"Removed all things from group {thing_group_name}")
        return True

    def delete_core_device(self):
        # Cancel all deployments for the core device
        try:
            deployments = self._ggClient.list_effective_deployments(
                coreDeviceThingName=self._thing_name)

            if not deployments.get('effectiveDeployments'):
                print("No deployments found to cancel")
            else:
                for deployment in deployments['effectiveDeployments']:
                    self._ggClient.cancel_deployment(
                        deploymentId=deployment['deploymentId'])
                print(f"Cancelled all deployments")

        except Exception as e:
            print(f"Error when listing deployments: {str(e)}")
            return False

        # Delete the core device
        try:
            self._ggClient.delete_core_device(
                coreDeviceThingName=self._thing_name)
            print(f"Deleted Greengrass core device '{self._thing_name}'")
        except Exception as e:
            print(f"Failed to delete Greengrass core device: {str(e)}")
            return False

        return True

    def delete_thing(self, thing_name: str):
        try:
            # Get certificates that attached to the thing
            principals = self._iotClient.list_thing_principals(
                thingName=thing_name)['principals']

            # For each certificate attached to the thing
            for principal in principals:
                cert_id = principal.split('/')[-1]

                # Detach all policies from the certificate
                policies = self._iotClient.list_attached_policies(
                    target=principal)['policies']

                for policy in policies:
                    self._iotClient.detach_policy(
                        policyName=policy['policyName'], target=principal)

                # Detach certificate from thing
                self._iotClient.detach_thing_principal(thingName=thing_name,
                                                       principal=principal)

                # Update certificate to INACTIVE
                self._iotClient.update_certificate(certificateId=cert_id,
                                                   newStatus='INACTIVE')

                # Delete the certificate
                self._iotClient.delete_certificate(certificateId=cert_id,
                                                   forceDelete=True)

            # Finally, delete the thing
            self._iotClient.delete_thing(thingName=thing_name)

            print(
                f"Successfully deleted thing '{thing_name}' and its associated certificates"
            )
            return True

        except Exception as e:
            print(f"Unexpected error deleting thing '{thing_name}': {str(e)}")
            return False

    def delete_thing_group(self, thing_group_name: str):
        try:
            self._iotClient.delete_thing_group(thingGroupName=thing_group_name)
            print(f"Successfully deleted thing group '{thing_group_name}'")
            return True

        except Exception as e:
            print(
                f"Unexpected error deleting thing group '{thing_group_name}': {str(e)}"
            )
            return False

    def clean_up(self, thing_name=None, thing_group_name=None):
        """
        Comprehensive cleanup method that handles both scenarios:
        1. When a thing is added to multiple thing groups
        2. When multiple things are added to a single thing group
        """
        print("\nRunning IoT clean up...")
        try:
            if thing_name:
                # Scenario 1: Clean up a specific thing and all its associated thing groups
                print(
                    f"Cleaning up thing '{thing_name}' and all its associated thing groups"
                )
                self.remove_all_thing_groups_from_thing(thing_name)

            if thing_group_name:
                # Scenario 2: Clean up a specific thing group and all its associated things
                print(
                    f"Cleaning up thing group '{thing_group_name}' and all its associated things"
                )
                self.remove_all_things_from_thing_group(thing_group_name)

            # Delete the core device
            self.delete_thing(self._thing_name)
            print("IoT clean-up completed.\n")

            # Delete the JSON file
            try:
                subprocess.run(['rm', '-rf', JSON_FILE], check=True)
            except Exception as e:
                print(f"Error when removing the JSON file, {str(e)}")

        except Exception as e:
            print(f"Error during cleanup: {str(e)}")

    # ===============================================
    # HELPER FUNCTIONS
    # ===============================================
    def _create_iot_role(self) -> str | None:
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
            role_response = self._iamClient.get_role(RoleName=role_name)
            print(f"Role '{role_name}' already exists.")
            return role_response['Role']['Arn']

        except self._iamClient.exceptions.NoSuchEntityException:
            role_response = self._iamClient.create_role(
                RoleName=role_name,
                AssumeRolePolicyDocument=json.dumps(trust_policy))

            policy_response = self._iamClient.create_policy(
                PolicyName=policy_name,
                PolicyDocument=json.dumps(token_exchange_policy))

            self._iamClient.attach_role_policy(
                RoleName=role_name, PolicyArn=policy_response['Policy']['Arn'])

            return role_response['Role']['Arn']

        except Exception as e:
            print(f"Error creating role: {str(e)}")
            return None

    def _create_role_alias(self, role_arn: str) -> str | None:
        role_alias_name = "ggl-uat-role-alias"

        try:
            response = self._iotClient.describe_role_alias(
                roleAlias=role_alias_name)
            print(f"Role alias '{role_alias_name}' already exists.")
            return response['roleAliasDescription']['roleAliasArn']

        except self._iotClient.exceptions.ResourceNotFoundException:
            response = self._iotClient.create_role_alias(
                roleAlias=role_alias_name,
                roleArn=role_arn,
                credentialDurationSeconds=3600)

            return response['roleAliasArn']

        except Exception as e:
            print(f"Error creating role alias: {str(e)}")
            return None

    def _attach_thing_policy(self, role_alias_arn: str, cert_arn: str):
        iot_policy_name = "ggl-uat-thing-policy"

        policy_document = {
            "Version":
            "2012-10-17",
            "Statement": [{
                "Effect":
                "Allow",
                "Action": [
                    "iot:Connect", "iot:Publish", "iot:Subscribe",
                    "iot:Receive", "greengrass:*"
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
            self._iotClient.get_policy(policyName=iot_policy_name)
            print(f"Policy '{iot_policy_name}' already exists.")

        except self._iotClient.exceptions.ResourceNotFoundException:
            self._iotClient.create_policy(
                policyName=iot_policy_name,
                policyDocument=json.dumps(policy_document))

        self._iotClient.attach_policy(policyName=iot_policy_name,
                                      target=cert_arn)
