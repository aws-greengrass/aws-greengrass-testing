from time import sleep, time
from typing import List, Optional
from boto3 import client
from types_boto3_iot import IoTClient
import botocore
from botocore.config import Config
from botocore.exceptions import ClientError, BotoCoreError
import json
import random
import subprocess
import uuid

JSON_FILE = "/tmp/aws-greengrass-testing-workspace/iot_setup_data.json"

# Adaptive retry config: adds client-side rate limiting/backoff to absorb
# IoT Core / GGv2 API throttling under parallel UAT load.
THROTTLE_RETRY_CONFIG = Config(retries={"max_attempts": 10, "mode": "adaptive"})

TEARDOWN_CALL_DELAY = 0.5    # seconds between destructive teardown calls to ease IoT Jobs DELETION_IN_PROGRESS limits

# Retryable throttling error codes from AWS APIs.
_THROTTLE_CODES = frozenset({
    "ThrottlingException",
    "Throttling",
    "ThrottledException",
    "TooManyRequestsException",
    "RequestLimitExceeded",
    "LimitExceededException",
})

# SendDirectMessage retry: 6 attempts, exponential backoff capped at 30s.
_DM_MAX_ATTEMPTS = 6
_DM_BASE_RETRY_DELAY = 2.0    # seconds
_DM_MAX_RETRY_DELAY = 30.0    # seconds
# Max seconds to wait for the device PUBACK (confirmation=True only).
_DM_CONFIRMATION_TIMEOUT = 10

# Failures that cannot succeed on retry.
_DM_NON_RETRYABLE_CODES = frozenset({
    "InvalidRequestException",
    "UnauthorizedException",
    "ForbiddenException",
    "RequestEntityTooLargeException",
})


def _retry_on_throttle(func, *, attempts=3, base_delay=1.0, cap=10.0):
    """Retry func() on throttling/transient errors with full-jitter backoff.

    Non-throttle ClientErrors (e.g. ResourceNotFoundException) re-raise immediately.
    """
    for attempt in range(attempts):
        try:
            return func()
        except ClientError as e:
            code = e.response["Error"].get("Code", "")
            if code not in _THROTTLE_CODES or attempt == attempts - 1:
                raise    # non-retryable or final attempt — propagate
            delay = random.uniform(0, min(base_delay * 2**attempt, cap))
            print(
                f"  Throttled ({code}), retry {attempt + 1}/{attempts} after {delay:.1f}s"
            )
            sleep(delay)
        except (BotoCoreError, botocore.exceptions.ConnectionError):
            if attempt == attempts - 1:
                raise
            delay = random.uniform(0, min(base_delay * 2**attempt, cap))
            print(
                f"  Transient error, retry {attempt + 1}/{attempts} after {delay:.1f}s"
            )
            sleep(delay)


class IoTUtils():

    def __init__(self, region: str, thing_name: str = None):
        self._region = region
        self._thing_name = thing_name
        self._iot_client = client("iot",
                                  region_name=self._region,
                                  config=THROTTLE_RETRY_CONFIG)
        self._iam_client = client("iam",
                                  region_name=self._region,
                                  config=THROTTLE_RETRY_CONFIG)
        self._gg_client = client("greengrassv2",
                                 region_name=self._region,
                                 config=THROTTLE_RETRY_CONFIG)
        # Built lazily on first data-plane use.
        self._iot_data_client = None
        self._thing_groups = []
        self._provisioned_role_name = None
        self._provisioned_role_alias = None

    @property
    def thing_name(self):
        return self._thing_name

    def get_iot_endpoints(self) -> dict:
        """Get IoT data and credential endpoints for this region."""
        data_ep = self._iot_client.describe_endpoint(
            endpointType="iot:Data-ATS")["endpointAddress"]
        cred_ep = self._iot_client.describe_endpoint(
            endpointType="iot:CredentialProvider")["endpointAddress"]
        return {"iotDataEndpoint": data_ep, "iotCredEndpoint": cred_ep}

    def send_direct_message(self,
                            client_id: str,
                            topic: str,
                            payload: str,
                            confirmation: bool = True,
                            timeout: int = _DM_CONFIRMATION_TIMEOUT) -> int:
        """Send an IoT Core direct message to a device by MQTT client id (the
        thing name), over its existing connection with no subscription.

        confirmation=True delivers at QoS 1 and waits for the device PUBACK,
        returning 504 on timeout so a send racing an unready receiver retries
        instead of passing falsely.

        Returns the HTTP status code (200 on success), fast-fails on
        non-retryable errors, and raises AssertionError if all attempts fail.
        """
        payload_bytes = payload.encode("utf-8") if isinstance(payload,
                                                              str) else payload
        iot_data_client = self._get_iot_data_client()
        last_error = None
        for attempt in range(1, _DM_MAX_ATTEMPTS + 1):
            try:
                response = iot_data_client.send_direct_message(
                    clientId=client_id,
                    topic=topic,
                    confirmation=confirmation,
                    timeout=timeout,
                    payload=payload_bytes)
                status = response["ResponseMetadata"]["HTTPStatusCode"]
                print(
                    f"SendDirectMessage to client {client_id} on topic {topic} "
                    f"succeeded: status={status} "
                    f"message={response.get('message')} "
                    f"traceId={response.get('traceId')}")
                return status
            except ClientError as e:
                code = e.response["Error"].get("Code", "")
                if code in _DM_NON_RETRYABLE_CODES:
                    # Cannot succeed on retry; surface it now.
                    print(f"ERROR: SendDirectMessage to client {client_id} on "
                          f"topic {topic} failed with non-retryable error "
                          f"{code}: {e}")
                    raise
                # Retryable (not-ready, throttling, 5xx, or PUBACK timeout).
                last_error = e
                print(f"SendDirectMessage to client {client_id} on topic "
                      f"{topic} failed on attempt {attempt}/{_DM_MAX_ATTEMPTS}: "
                      f"{e}")
            except (BotoCoreError, botocore.exceptions.ConnectionError) as e:
                # Transient transport error.
                last_error = e
                print(f"SendDirectMessage to client {client_id} on topic "
                      f"{topic} transient error on attempt "
                      f"{attempt}/{_DM_MAX_ATTEMPTS}: {e}")

            if attempt < _DM_MAX_ATTEMPTS:
                sleep(
                    min(_DM_BASE_RETRY_DELAY * 2**(attempt - 1),
                        _DM_MAX_RETRY_DELAY))

        raise AssertionError(
            f"SendDirectMessage to client {client_id} on topic {topic} did not "
            f"succeed after {_DM_MAX_ATTEMPTS} attempts") from last_error

    def publish_to_iot_core(self,
                            topic: str,
                            payload: str,
                            qos: int = 1) -> int:
        """Publish a message to an IoT Core topic (broker publish). Returns the
        HTTP status code (200 on success)."""
        payload_bytes = payload.encode("utf-8") if isinstance(payload,
                                                              str) else payload
        response = self._get_iot_data_client().publish(topic=topic,
                                                       qos=qos,
                                                       payload=payload_bytes)
        status = response["ResponseMetadata"]["HTTPStatusCode"]
        print(f"Publish to IoT Core on topic {topic} returned status={status}")
        return status

    def provision_for_endpoint_switch(self,
                                      cert_pem: str,
                                      role_alias_name: str,
                                      role_name: str = "ggl-uat-role"):
        """Provision IoT resources for an existing device in this
        region. Registers the certificate PEM (without CA) and
        creates thing, policy, role, and role alias."""
        self._iot_client.create_thing(thingName=self._thing_name)
        resp = self._iot_client.register_certificate_without_ca(
            certificatePem=cert_pem, status='ACTIVE')
        cert_arn = resp['certificateArn']
        self._iot_client.attach_thing_principal(thingName=self._thing_name,
                                                principal=cert_arn)
        role_arn, role_created = self._create_iot_role(role_name=role_name)
        role_alias_arn, alias_created = self._create_role_alias(
            role_arn, role_alias_name)
        if role_created:
            self._provisioned_role_name = role_name
        if alias_created:
            self._provisioned_role_alias = role_alias_name
        self._attach_thing_policy(role_alias_arn, cert_arn,
                                  "ggl-uat-thing-policy-dest")
        print(f"Provisioned thing '{self._thing_name}' in {self._region}")

    def generate_random_id(self):
        return str(uuid.uuid4().hex)

    def generate_thing_name(self, id):
        return "ggl-uat-thing-" + id

    def generate_thing_group_name(self, id):
        return "ggl-uat-thing-group-" + id

    def set_up_core_device(self):
        id = self.generate_random_id()
        self._thing_name = self.generate_thing_name(id)
        device_cert, private_key = self.create_new_thing(self._thing_name)

        data = {
            "DEVICE_CERT": device_cert,
            "PRIVATE_KEY": private_key,
            "THING_NAME": self._thing_name
        }

        with open(JSON_FILE, 'w') as f:
            json.dump(data, f)

    def create_new_thing(self, thing_name: str) -> list | None:
        try:
            thing_response = self._iot_client.create_thing(thingName=thing_name)
            cert_response = self._iot_client.create_keys_and_certificate(
                setAsActive=True)

            cert_arn = cert_response['certificateArn']

            self._iot_client.attach_thing_principal(thingName=thing_name,
                                                    principal=cert_arn)

        except Exception as error:
            print(f"Error when creating thing: {str(error)}")
            return None

        # set up role and role alias
        role_arn, _ = self._create_iot_role()
        role_alias_arn, _ = self._create_role_alias(role_arn)
        self._attach_thing_policy(role_alias_arn, cert_arn)

        print(f"Successfully created a thing: {thing_name}")
        return [
            cert_response['certificatePem'],
            cert_response['keyPair']['PrivateKey']
        ]

    def create_new_thing_group(self, thing_group_name: str) -> bool:
        response = self._iot_client.create_thing_group(
            thingGroupName=thing_group_name)
        if response is not None:
            self._thing_groups.append(response['thingGroupName'])
            print(f"Successfully created a thing group: {thing_group_name}")
            return True

        return False

    def add_thing_to_thing_group(self, thing_name: str,
                                 thing_group_name: str) -> bool:
        # Check if the thing group exists
        try:
            self._iot_client.describe_thing_group(
                thingGroupName=thing_group_name)
        except self._iot_client.exceptions.ResourceNotFoundException:
            # Thing group doesn't exist, create it
            self.create_new_thing_group(thing_group_name)

        response = self._iot_client.add_thing_to_thing_group(
            thingName=thing_name, thingGroupName=thing_group_name)

        if response['ResponseMetadata']['HTTPStatusCode'] == 200:
            print(
                f"Successfully added thing '{thing_name}' to thing group '{thing_group_name}'"
            )
            return True
        return False

    def remove_thing_from_thing_group(self, thing_name: str,
                                      thing_group_name: str) -> bool | None:
        try:
            # Check if the thing group exists
            self._iot_client.describe_thing_group(
                thingGroupName=thing_group_name)

            # Check if the thing is in the group before attempting removal
            response = self._iot_client.list_things_in_thing_group(
                thingGroupName=thing_group_name, recursive=True)

            # If the thing is not in the group, return False
            if thing_name not in response.get('things', []):
                return False

            # Proceed with removal
            self._iot_client.remove_thing_from_thing_group(
                thingName=thing_name, thingGroupName=thing_group_name)
            return True
        except self._iot_client.exceptions.ResourceNotFoundException:
            print(f"Thing group {thing_group_name} does not exist")
            return None
        except self._iot_client.exceptions.ClientError as e:
            print(
                f"Error removing thing '{thing_name}' from thing group '{thing_group_name}': {e}"
            )
            return False

    def delete_core_device(self):
        # Cancel all deployments for the core device
        try:
            deployments = self._gg_client.list_effective_deployments(
                coreDeviceThingName=self._thing_name)

            if not deployments.get('effectiveDeployments'):
                print("No deployments found to cancel")
            else:
                for deployment in deployments['effectiveDeployments']:
                    # Isolate each cancel so one throttle/error doesn't abort the rest.
                    try:
                        self._gg_client.cancel_deployment(
                            deploymentId=deployment['deploymentId'])
                    except Exception as e:
                        print(
                            f"Failed to cancel deployment {deployment['deploymentId']}: {e}"
                        )
                    sleep(TEARDOWN_CALL_DELAY)
                print(f"Cancelled all deployments")

        except Exception as e:
            print(f"Error when listing deployments: {str(e)}")
            return False

        # Delete the core device
        try:
            _retry_on_throttle(lambda: self._gg_client.delete_core_device(
                coreDeviceThingName=self._thing_name),
                               attempts=5,
                               base_delay=2.0,
                               cap=30.0)
            print(f"Deleted Greengrass core device '{self._thing_name}'")
        except Exception as e:
            print(f"Failed to delete Greengrass core device: {str(e)}")
            return False

        return True

    def delete_thing(self, thing_name: str):
        try:
            # Get certificates that attached to the thing
            principals = self._iot_client.list_thing_principals(
                thingName=thing_name)['principals']

            # For each certificate attached to the thing — isolate each so
            # a failure on one cert doesn't leak the others.
            for principal in principals:
                try:
                    cert_id = principal.split('/')[-1]

                    # Detach all policies from the certificate
                    policies = self._iot_client.list_attached_policies(
                        target=principal)['policies']

                    for policy in policies:
                        self._iot_client.detach_policy(
                            policyName=policy['policyName'], target=principal)

                    # Detach certificate from thing
                    self._iot_client.detach_thing_principal(
                        thingName=thing_name, principal=principal)

                    # Update certificate to INACTIVE
                    self._iot_client.update_certificate(certificateId=cert_id,
                                                        newStatus='INACTIVE')

                    # Delete the certificate
                    self._iot_client.delete_certificate(certificateId=cert_id,
                                                        forceDelete=True)
                except Exception as e:
                    print(
                        f"Error cleaning up cert {principal} for thing '{thing_name}': {e}"
                    )
                sleep(TEARDOWN_CALL_DELAY)

            # Finally, delete the thing
            _retry_on_throttle(
                lambda: self._iot_client.delete_thing(thingName=thing_name),
                attempts=5,
                base_delay=2.0,
                cap=30.0)

            print(
                f"Successfully deleted thing '{thing_name}' and its associated certificates"
            )
            return True

        except self._iot_client.exceptions.ResourceNotFoundException:
            print(f"Thing '{thing_name}' does not exist, nothing to delete")
            return True

        except Exception as e:
            print(f"Unexpected error deleting thing '{thing_name}': {str(e)}")
            return False

    def delete_thing_group(self, thing_group_name: str):
        try:
            self._iot_client.delete_thing_group(thingGroupName=thing_group_name)
            print(f"Successfully deleted thing group '{thing_group_name}'")
            return True

        except self._iot_client.exceptions.ResourceNotFoundException:
            print(
                f"Thing group '{thing_group_name}' does not exist, nothing to delete"
            )
            return True

        except Exception as e:
            print(
                f"Unexpected error deleting thing group '{thing_group_name}': {str(e)}"
            )
            return False

    def clean_up(self):
        print("\nRunning IoT clean up...")
        # Isolate per-resource so the first failure doesn't skip the rest.
        for thing_group in self._thing_groups:
            try:
                self.remove_thing_from_thing_group(self._thing_name,
                                                   thing_group)
            except Exception as e:
                print(f"Error removing thing from group {thing_group}: {e}")
            try:
                self.delete_thing_group(thing_group)
            except Exception as e:
                print(f"Error deleting thing group {thing_group}: {e}")
            sleep(TEARDOWN_CALL_DELAY)

        # Delete the core device
        try:
            self.delete_core_device()
        except Exception as e:
            print(f"Error deleting core device: {e}")
        sleep(TEARDOWN_CALL_DELAY)

        try:
            self.delete_thing(self._thing_name)

            # Delete provisioned role and alias if created
            if self._provisioned_role_alias:
                try:
                    self._iot_client.delete_role_alias(
                        roleAlias=self._provisioned_role_alias)
                    print(
                        f"Deleted role alias '{self._provisioned_role_alias}'")
                except Exception as e:
                    print(f"Could not delete role alias: {e}")
            if self._provisioned_role_name:
                try:
                    attached = self._iam_client.list_attached_role_policies(
                        RoleName=self._provisioned_role_name
                    )['AttachedPolicies']
                    for policy in attached:
                        self._iam_client.detach_role_policy(
                            RoleName=self._provisioned_role_name,
                            PolicyArn=policy['PolicyArn'])
                        self._iam_client.delete_policy(
                            PolicyArn=policy['PolicyArn'])
                    inline = self._iam_client.list_role_policies(
                        RoleName=self._provisioned_role_name)['PolicyNames']
                    for policy_name in inline:
                        self._iam_client.delete_role_policy(
                            RoleName=self._provisioned_role_name,
                            PolicyName=policy_name)
                    self._iam_client.delete_role(
                        RoleName=self._provisioned_role_name)
                    print(f"Deleted role '{self._provisioned_role_name}'")
                except Exception as e:
                    print(f"Could not delete role: {e}")

            print("IoT clean-up completed.\n")
        except Exception as e:
            print(f"Error deleting thing {self._thing_name}: {e}")

        # Delete the JSON file
        try:
            subprocess.run(['rm', '-rf', JSON_FILE], check=True)
        except Exception as e:
            print(f"Error when removing the JSON file, {str(e)}")

    # ===============================================
    # HELPER FUNCTIONS
    # ===============================================
    def _get_iot_data_client(self):
        """Build the IoT data plane client on first use, pointed at the
        account's iot:Data-ATS endpoint."""
        if self._iot_data_client is None:
            data_endpoint = self._iot_client.describe_endpoint(
                endpointType="iot:Data-ATS")["endpointAddress"]
            self._iot_data_client = client("iot-data",
                                           region_name=self._region,
                                           endpoint_url=f"https://{data_endpoint}",
                                           config=THROTTLE_RETRY_CONFIG)
        return self._iot_data_client

    def _create_iot_role(self,
                         role_name: str = "ggl-uat-role"
                         ) -> tuple[str | None, bool]:
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
                    "logs:PutLogEvents", "logs:DescribeLogStreams",
                    "iot:DescribeEndpoint", "s3:*"
                ],
                "Resource":
                "*"
            }]
        }

        policy_name = f"{role_name}-token-exchange-policy"

        try:
            role_response = self._iam_client.get_role(RoleName=role_name)
            role_arn = role_response['Role']['Arn']
            role_created = False
            print(f"Role '{role_name}' already exists.")

        except self._iam_client.exceptions.NoSuchEntityException:
            role_response = self._iam_client.create_role(
                RoleName=role_name,
                AssumeRolePolicyDocument=json.dumps(trust_policy))
            role_arn = role_response['Role']['Arn']
            role_created = True

        except Exception as e:
            print(f"Error creating role: {str(e)}")
            return (None, False)

        # Always reconcile the inline policy with the current template.
        # put_role_policy is upsert (creates or overwrites), so this picks up
        # template changes on roles that persist across runs.
        try:
            self._iam_client.put_role_policy(
                RoleName=role_name,
                PolicyName=policy_name,
                PolicyDocument=json.dumps(token_exchange_policy))
        except Exception as e:
            print(f"Error reconciling role policy: {str(e)}")
            return (None, False)

        return (role_arn, role_created)

    def _create_role_alias(self,
                           role_arn: str,
                           role_alias_name: str = "ggl-uat-role-alias"
                           ) -> tuple[str | None, bool]:

        try:
            response = self._iot_client.describe_role_alias(
                roleAlias=role_alias_name)
            existing_arn = response['roleAliasDescription']['roleArn']
            if existing_arn != role_arn:
                # Reconcile: alias exists but points to a different role.
                # Without this, a stale role alias from a previous test run
                # silently overrides the intended role_arn.
                print(f"Role alias '{role_alias_name}' points to "
                      f"'{existing_arn}', updating to '{role_arn}'.")
                self._iot_client.update_role_alias(roleAlias=role_alias_name,
                                                   roleArn=role_arn)
            else:
                print(f"Role alias '{role_alias_name}' already exists.")
            return (response['roleAliasDescription']['roleAliasArn'], False)

        except self._iot_client.exceptions.ResourceNotFoundException:
            response = self._iot_client.create_role_alias(
                roleAlias=role_alias_name,
                roleArn=role_arn,
                credentialDurationSeconds=3600)

            return (response['roleAliasArn'], True)

        except Exception as e:
            print(f"Error creating role alias: {str(e)}")
            return (None, False)

    def _attach_thing_policy(self,
                             role_alias_arn: str,
                             cert_arn: str,
                             policy_name: str = "ggl-uat-thing-policy"):

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
            self._iot_client.get_policy(policyName=policy_name)
            print(f"Policy '{policy_name}' already exists.")

        except self._iot_client.exceptions.ResourceNotFoundException:
            self._iot_client.create_policy(
                policyName=policy_name,
                policyDocument=json.dumps(policy_document))

        self._iot_client.attach_policy(policyName=policy_name, target=cert_arn)
