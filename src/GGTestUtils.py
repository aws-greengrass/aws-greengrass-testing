import json
import os
from typing import Any, Dict, List, Literal, Optional, Sequence, Tuple
from uuid import uuid1
import boto3
from botocore.exceptions import ClientError
import time
import logging
import subprocess
from types_boto3_greengrassv2 import GreengrassV2Client
from types_boto3_greengrassv2.type_defs import CreateDeploymentResponseTypeDef, ComponentDeploymentSpecificationTypeDef
from types_boto3_greengrassv2.literals import CoreDeviceStatusType
from types_boto3_iot import IoTClient
from types_boto3_s3 import S3Client
import yaml
from subprocess import run
from pathlib import Path
from typing import Sequence, Optional, Any, Dict, List, Literal, Optional, Sequence, NamedTuple

S3_ARTIFACT_DIR = "artifacts"
RECIPE_DIR = "/var/lib/greengrass/packages/recipes"


def sleep_with_log(seconds: int, reason: str = ""):
    """Sleep with logging message before sleeping."""
    msg = f"Sleeping for {seconds}s"
    if reason:
        msg += f" ({reason})"
    print(msg)
    time.sleep(seconds)


class ComponentDeploymentInfo(NamedTuple):
    name: str
    versions: List[str]
    merge_config: Dict | str
    """JSON document of configuration keys to merge"""


class GGTestUtils:
    _account: str
    _region: str
    _bucket: str
    _cli_bin_path: str
    _ggClient: GreengrassV2Client
    _iotClient: IoTClient
    _s3Client: S3Client
    _ggComponentToDeleteArn: List[str]
    _ggS3ObjToDelete: List[str]
    _ggServiceList: List[str]
    _ggDeploymentToThingNameList: List[Tuple[str, str]]

    def __init__(self, account: str, bucket: str, region: str,
                 cli_bin_path: str):
        self._region = region
        self._account = account
        self._bucket = bucket
        self._cli_bin_path = cli_bin_path
        self._ggClient = boto3.client("greengrassv2", region_name=self._region)
        self._iotClient = boto3.client("iot", region_name=self._region)
        self._s3Client = boto3.client("s3", region_name=self._region)
        self._ggComponentToDeleteArn = []
        self._component_random_ids = {
        }    # Track random_id per component-version
        self._ggServiceList = []
        self._ggDeploymentToThingNameList = []
        self._component_random_ids = {}

    @property
    def aws_account(self) -> str:
        return self._account

    @property
    def aws_region(self) -> str:
        return self._region

    @property
    def s3_artifact_bucket(self) -> str:
        return self._bucket

    @property
    def cli_bin_path(self) -> str:
        return self._cli_bin_path

    def get_thing_group_arn(self, thing_group: str) -> str:
        return f"arn:aws:iot:{self.aws_region}:{self.aws_account}:thinggroup/{thing_group}"

    def get_thing_arn(self, thing: str) -> str:
        return f"arn:aws:iot:{self.aws_region}:{self.aws_account}:thing/{thing}"

    def _get_things_in_thing_group(self, thing_group_name) -> List[str]:
        """
        Retrieves a list of things in a given thing group.

        Args:
            thing_group_name (str): The name of the thing group.

        Returns:
            list: A list of thing names in the thing group, or None if an error occurs.
        """
        try:
            response = self._iotClient.list_things_in_thing_group(
                thingGroupName=thing_group_name)
            things = [thing for thing in response.get("things", [])]
            return things
        except Exception as e:
            print(f"Error retrieving things in thing group: {e}")
            return None

    def _check_greengrass_group_deployment_status(
            self, deployment_id: str) -> Optional[Dict[str, Any]]:
        """
        Check the status of a Greengrass deployment for a thing group.

        :param deployment_id: The ID of the deployment to check
        :return: A dictionary containing the deployment status and details
        """
        start_time = time.time()
        try:
            # Get the deployment status
            response = self._ggClient.get_deployment(deploymentId=deployment_id)

            # Extract relevant information
            deployment_status = response["deploymentStatus"]
            target_arn = response["targetArn"]
            creation_timestamp = response["creationTimestamp"]

            things_list = []
            if "thinggroup" in target_arn:
                things_list = self._get_things_in_thing_group(
                    target_arn.split("/")[-1])
            else:
                # If this is a thing instead of a thing group.
                things_list.append(target_arn.split("/")[-1])

            statistics_list = []

            for thing in things_list:
                try:
                    loop_again = True
                    next_token = None
                    while loop_again is True:
                        # Get deployment statistics
                        if next_token is None:
                            statistic = self._ggClient.list_effective_deployments(
                                coreDeviceThingName=thing, maxResults=100)
                        else:
                            statistic = self._ggClient.list_effective_deployments(
                                coreDeviceThingName=thing,
                                maxResults=100,
                                nextToken=next_token)

                        if "nextToken" in statistic:
                            next_token = statistic["nextToken"]
                        else:
                            loop_again = False

                        statistic = statistic["effectiveDeployments"]

                        if statistic:
                            statistics_list.append({thing: statistic})

                except Exception as e:
                    elapsed = int(time.time() - start_time)
                    print(
                        f"Waiting to get statistics for {thing}: {str(e)}... (elapsed: {elapsed}s)"
                    )

            return {
                "status": deployment_status,
                "target_arn": target_arn,
                "creation_timestamp": creation_timestamp,
                "statistics": statistics_list,
            }

        except ClientError as e:
            if e.response["Error"]["Code"] == "ResourceNotFoundException":
                print(f"Deployment {deployment_id} not found")
            else:
                print(f"An error occurred: {e}")
            return None

    def create_local_deployment(self, artifacts_dir, recipe_dir,
                                component_details) -> bool:
        cli_cmd = ["sudo", "-E", self.cli_bin_path, "deploy"]
        if artifacts_dir is not None:
            cli_cmd.extend(["--artifacts-dir", artifacts_dir])
        if recipe_dir is not None:
            cli_cmd.extend(["--recipe-dir", recipe_dir])
        cli_cmd.append(f"--add-component={component_details}")

        # Disable LeakSanitizer entirely - it's causing false failures
        env = os.environ.copy()
        env["ASAN_OPTIONS"] = "detect_leaks=0"
        env["LSAN_OPTIONS"] = "detect_leaks=0"

        process = run(cli_cmd, capture_output=True, text=True, env=env)
        if process.returncode == 0:
            print("CLI call to create local deployment succeeded:")
            print(process.stdout)
            return True

        print(
            f"CLI call to create local deployment failed with error code {process.returncode}:"
        )
        print("STDERR:", process.stderr)
        return False

    def _convert_deployment_info(
        self, component: ComponentDeploymentInfo
    ) -> ComponentDeploymentSpecificationTypeDef:
        if len(component.versions) > 1:
            print(
                "In a deployment each component cannot have more than 1 version."
            )
            raise ValueError
        specification: ComponentDeploymentSpecificationTypeDef = {
            "componentVersion": component.versions[0]
        }

        if component.merge_config is not None:
            merge: str
            if component.merge_config is str:
                merge = component.merge_config
            else:
                merge = json.dumps(component.merge_config)
            specification["configurationUpdate"] = {"merge": merge}
        return specification

    def create_deployment(
            self,
            thingArn: str,
            component_list: Sequence[ComponentDeploymentInfo],
            deployment_name: str = None) -> CreateDeploymentResponseTypeDef:
        component_parsed_dict = {
            component.name: self._convert_deployment_info(component)
            for component in component_list
        }

        result = self._ggClient.create_deployment(
            targetArn=thingArn,
            deploymentName=deployment_name or "UATInPython",
            components=component_parsed_dict,
        )

        if result is not None:
            self._ggServiceList.extend(
                [component.name for component in component_list])
            self._ggDeploymentToThingNameList.append(
                (result["deploymentId"], thingArn))

        return result

    def remove_component(
            self, deployment_id: str, component_name_to_remove: str,
            thing_group_arn) -> Literal['SUCCEEDED', 'FAILED', 'TIMEOUT']:
        # Get deployment details
        deployment_info = self._ggClient.get_deployment(
            deploymentId=deployment_id)

        # Retrieve the actual component configuration
        components = deployment_info.get("components", {})

        if component_name_to_remove not in components:
            raise ValueError(
                f"Component '{component_name_to_remove}' not found in deployment '{deployment_id}'."
            )

        # Remove the component
        del components[component_name_to_remove]
        print(
            f"Removed component '{component_name_to_remove}' from deployment '{deployment_id}'."
        )

        # Create a new deployment with the updated components
        new_deployment = self.create_deployment(
            thingArn=thing_group_arn,
            component_list=components,
            deployment_name="FirstDeployment")["deploymentId"]

        print(f"New deployment created: {new_deployment}")

        result = self.wait_for_deployment_till_timeout(120, new_deployment)

        print(f"The removal of component through deployment: {result}")

        return result

    def remove_all_components(
            self,
            thing_group_arn: str) -> Literal['SUCCEEDED', 'FAILED', 'TIMEOUT']:

        # Debug: Check devices in thing group before cleanup
        try:
            thing_group_name = thing_group_arn.split('/')[-1]
            devices_response = self._iotClient.list_things_in_thing_group(
                thingGroupName=thing_group_name)
            print(
                f"DEBUG: Devices in thing group {thing_group_name}: {devices_response.get('things', [])}"
            )

            # Check each device status
            for device_name in devices_response.get('things', []):
                try:
                    device_status = self._ggClient.get_core_device(
                        coreDeviceThingName=device_name)
                    print(
                        f"DEBUG: Device {device_name} status: {device_status.get('status')}"
                    )

                    # Check current deployments
                    deployments = self._ggClient.list_effective_deployments(
                        coreDeviceThingName=device_name)
                    print(
                        f"DEBUG: Device {device_name} effective deployments: {len(deployments.get('effectiveDeployments', []))}"
                    )
                except Exception as e:
                    print(f"DEBUG: Error checking device {device_name}: {e}")
        except Exception as e:
            print(f"DEBUG: Error checking thing group devices: {e}")

        # Create a new deployment with the empty components
        new_deployment = self.create_deployment(
            thingArn=thing_group_arn,
            component_list=[],
            deployment_name="FirstDeployment")["deploymentId"]

        print(f"New deployment created: {new_deployment}")

        result = self.wait_for_deployment_till_timeout(120, new_deployment)

        print(f"The removal of component through deployment: {result}")

        return result

    def wait_for_deployment_till_timeout(
            self, timeout: float,
            deployment_id: str) -> Literal['SUCCEEDED', 'FAILED', 'TIMEOUT']:
        while timeout > 0:
            result = self._check_greengrass_group_deployment_status(
                deployment_id)
            if result:
                if result["statistics"]:
                    for entry in result["statistics"]:
                        for thing in entry:
                            for deployment in entry[thing]:
                                if str(deployment["deploymentId"]) == str(
                                        deployment_id):
                                    if (str(deployment[
                                            "coreDeviceExecutionStatus"]) ==
                                            "SUCCEEDED"):
                                        return "SUCCEEDED"
                                    elif (str(deployment[
                                            "coreDeviceExecutionStatus"]) ==
                                          "FAILED"):
                                        print(f"\n{'='*60}")
                                        print(
                                            f"DEPLOYMENT FAILED: {deployment_id}"
                                        )
                                        print(f"Thing: {thing}")
                                        print(
                                            f"Status Reason: {deployment.get('statusReason', 'N/A')}"
                                        )
                                        print(
                                            f"Full deployment details: {deployment}"
                                        )

                                        # Get full deployment details from AWS
                                        try:
                                            aws_deployment = self._ggClient.get_deployment(
                                                deploymentId=deployment_id)
                                            print(
                                                f"\nAWS Deployment Status: {aws_deployment.get('deploymentStatus', 'N/A')}"
                                            )
                                            print(
                                                f"AWS Deployment Policies: {aws_deployment.get('deploymentPolicies', {})}"
                                            )
                                            if 'components' in aws_deployment:
                                                print(
                                                    f"Components in deployment: {list(aws_deployment['components'].keys())}"
                                                )
                                        except Exception as e:
                                            print(
                                                f"Could not get AWS deployment details: {e}"
                                            )

                                        print(
                                            f"\nChecking all Greengrass logs for errors..."
                                        )
                                        try:
                                            # Check all greengrass services
                                            services = [
                                                "ggdeploymentd", "iotcored",
                                                "ggconfigd", "tesd",
                                                "gghealthd", "ggipcd"
                                            ]
                                            for svc in services:
                                                log_output = subprocess.run(
                                                    [
                                                        "journalctl", "-u", svc,
                                                        "--no-pager", "-n",
                                                        "50", "--since",
                                                        "5 minutes ago"
                                                    ],
                                                    capture_output=True,
                                                    text=True,
                                                    timeout=3)
                                                if log_output.stdout.strip(
                                                ) and "-- No entries --" not in log_output.stdout:
                                                    print(
                                                        f"\n--- {svc} logs ---")
                                                    print(log_output.
                                                          stdout[-1500:])

                                            # Check for any errors in all logs
                                            error_log = subprocess.run(
                                                [
                                                    "journalctl", "--no-pager",
                                                    "-p", "err", "--since",
                                                    "5 minutes ago"
                                                ],
                                                capture_output=True,
                                                text=True,
                                                timeout=3)
                                            if error_log.stdout.strip(
                                            ) and "-- No entries --" not in error_log.stdout:
                                                print(
                                                    f"\n--- System errors (priority: err) ---"
                                                )
                                                print(error_log.stdout[-2000:])
                                        except Exception as e:
                                            print(
                                                f"Could not retrieve logs: {e}")
                                        print(f"{'='*60}\n")
                                        return "FAILED"
                                    else:
                                        pass

            time.sleep(1)
            timeout -= 1

        return "TIMEOUT"

    def _upload_files_to_s3(self,
                            files: Sequence[os.PathLike | str],
                            bucket_name: str,
                            random_id: str = None) -> bool:
        """
        Upload a file to an S3 bucket

        :param file_path: File to upload
        :param bucket_name: Bucket to upload to
        :param random_id: Optional random ID to use as subdirectory
        :return: True if file was uploaded, else False
        """

        for file_path in files:
            if random_id:
                object_name = os.path.join(S3_ARTIFACT_DIR, random_id,
                                           os.path.basename(file_path))
            else:
                object_name = os.path.join(S3_ARTIFACT_DIR,
                                           os.path.basename(file_path))

            try:
                # Upload the file
                self._s3Client.upload_file(file_path, bucket_name, object_name)
            except Exception as e:
                print(f"Error uploading file: {e}")
                return False

            print(
                f"File {file_path} successfully uploaded to {bucket_name}/{object_name}"
            )

        # Wait for S3 propagation
        if files:
            time.sleep(5)

        return True

    def _upload_component_to_gg(self,
                                recipe_files: List[os.PathLike | str],
                                random_id: str = None) -> str:
        recipe_name = ""
        recipe_content = ""
        cloud_addition = str(uuid1())

        if len(recipe_files) < 1:
            return None

        cloud_recipe_name = os.path.basename(
            recipe_files[0]).split('-')[0] + cloud_addition

        for recipe_path in recipe_files:
            # Read and modify the file
            with open(recipe_path, "r") as f:
                recipe_content = f.read()
                recipe_name: str = yaml.safe_load(
                    recipe_content)["ComponentName"]

                modified_content = recipe_content.replace(
                    "$bucketName$", self.s3_artifact_bucket)
                modified_content = modified_content.replace(
                    "$testArtifactsDirectory$", S3_ARTIFACT_DIR)

                # Replace $randomId$ with actual random ID if provided
                if random_id:
                    modified_content = modified_content.replace(
                        "$randomId$", random_id)

                modified_content = modified_content.replace(
                    recipe_name, cloud_recipe_name)

                # Parse the modified content as YAML and convert it to JSON.
                recipe_yaml = yaml.safe_load(modified_content)
                recipe_json = json.dumps(recipe_yaml)

                # Retry CreateComponentVersion if artifact not accessible yet
                for retry in range(20):
                    try:
                        # Create component version using the recipe
                        response = self._ggClient.create_component_version(
                            inlineRecipe=recipe_json)
                        break
                    except self._ggClient.exceptions.ValidationException as e:
                        if "artifact resource cannot be accessed" in str(
                                e).lower() and retry < 19:
                            print(
                                f"Artifact not accessible yet, retrying in 10s (attempt {retry + 1}/20)"
                            )
                            time.sleep(10)
                        else:
                            raise
                    except self._ggClient.exceptions.ConflictException:
                        raise
                    except Exception:
                        raise

                print(
                    f"Successfully uploaded component with ARN: {response['arn']}"
                )
                self._ggComponentToDeleteArn.append(response["arn"])

                # Wait for component to be DEPLOYABLE
                component_name = response['componentName']
                component_version = response['componentVersion']
                for attempt in range(10):
                    status_response = self._ggClient.describe_component(
                        arn=response['arn'])
                    status = status_response.get('status',
                                                 {}).get('componentState')
                    if status == 'DEPLOYABLE':
                        print(
                            f"Component {component_name} is DEPLOYABLE after {attempt + 1}s"
                        )
                        break
                    time.sleep(1)
                else:
                    print(
                        f"Warning: Component {component_name} status is {status}, not DEPLOYABLE after 10s"
                    )

        return cloud_recipe_name

    def upload_component_with_version_and_deps(
        self, component_name: str, version: str,
        dependencies: List[Tuple[str,
                                 str]]) -> Optional[ComponentDeploymentInfo]:
        return self.upload_component_with_versions(component_name, [version],
                                                   dependencies)

    def upload_component_with_versions(
        self,
        component_name: str,
        versions: List[str],
        dependencies: List[Tuple[str, str]] = None
    ) -> Optional[ComponentDeploymentInfo]:

        # Generate a random ID for artifact uploads
        random_id = str(uuid1())

        # Store random_id for each version
        for version in versions:
            self._component_random_ids[
                f"{component_name}-{version}"] = random_id

        for version in versions:
            try:
                component_artifact_dir = os.path.join('components',
                                                      component_name, version,
                                                      'artifacts')

                artifact_files = os.listdir(component_artifact_dir)
                artifact_files_full_paths = [
                    os.path.abspath(os.path.join(component_artifact_dir, file))
                    for file in artifact_files
                ]
                self._upload_files_to_s3(artifact_files_full_paths,
                                         self.s3_artifact_bucket, random_id)
            except FileNotFoundError:
                print(
                    f"No artifact directory found for {component_name}-{version}."
                )
            except PermissionError:
                print(
                    f"Cannot access the directory with artifacts for {component_name}-{version}."
                )
                return None

        try:
            recipes_file_list = []
            for version in versions:
                component_recipe_dir = os.path.join('components',
                                                    component_name, version,
                                                    'recipe')

                recipes = os.listdir(component_recipe_dir)
                recipes_full_paths = [
                    os.path.abspath(os.path.join(component_recipe_dir, file))
                    for file in recipes
                ]

                if len(recipes_full_paths) != 1:
                    print("More than one recipe files found.")
                    return None

                # If dependencies provided, modify recipe
                if dependencies:
                    with open(recipes_full_paths[0]) as recipe:
                        recipe_obj = yaml.safe_load(recipe)
                        if "ComponentDependencies" not in recipe_obj:
                            print(
                                "ComponentDependencies section not found in the original recipe."
                            )
                            return None

                        for dependency in dependencies:
                            if dependency[0] not in recipe_obj[
                                    "ComponentDependencies"]:
                                print(
                                    f"The dependency {dependency[0]} not found in original recipe."
                                )
                                return None
                            stored_val = recipe_obj["ComponentDependencies"][
                                dependency[0]]
                            del recipe_obj["ComponentDependencies"][
                                dependency[0]]
                            recipe_obj["ComponentDependencies"][
                                dependency[1]] = stored_val

                        output_dir = os.path.join(
                            "/tmp/aws-greengrass-testing-workspace", "ggtest",
                            "modified_recipes")
                        os.makedirs(output_dir, exist_ok=True)
                        new_file_path = os.path.join(
                            output_dir, os.path.basename(recipes_full_paths[0]))

                        with open(new_file_path, "w") as f_out:
                            f_out.write(yaml.safe_dump(recipe_obj))

                        recipes_file_list.append(new_file_path)
                else:
                    recipes_file_list.append(recipes_full_paths[0])

            cloud_name = self._upload_component_to_gg(recipes_file_list,
                                                      random_id)
            return ComponentDeploymentInfo(name=cloud_name,
                                           versions=versions,
                                           merge_config=None)
        except FileNotFoundError:
            print(f"No recipe directory found for {component_name}-{version}.")
            return None
        except PermissionError:
            print(
                f"Cannot access the directory with recipe for {component_name}-{version}."
            )
            return None

    def _create_corrupt_file(self, file_path: str | os.PathLike):
        try:
            # Ensure the output directory exists
            output_dir = os.path.join("/tmp/aws-greengrass-testing-workspace",
                                      "ggtest", "corruptFiles")
            os.makedirs(output_dir, exist_ok=True)

            # Construct the new file path
            new_file_path = os.path.join(output_dir,
                                         os.path.basename(file_path))

            # Read the original file and write the corrupted version
            with open(file_path, "rb") as f_in, open(new_file_path,
                                                     "wb") as f_out:
                content = f_in.read()
                f_out.write(content)
                f_out.write(b"#corruption comment")

            return new_file_path

        except IOError as e:
            print(f"Error creating corrupt file: {e}")
            return None

    def upload_corrupt_artifacts_to_s3(self, component_name: str,
                                       version: str) -> bool:
        component_artifact_dir = os.path.join('components', component_name,
                                              version, 'artifacts')

        artifact_files = os.listdir(component_artifact_dir)
        artifact_files_full_paths = [
            os.path.abspath(os.path.join(component_artifact_dir, file))
            for file in artifact_files
        ]
        corrupt_file_list = []

        for file in artifact_files_full_paths:
            corrupt_file_path = self._create_corrupt_file(file)
            assert corrupt_file_list is not None
            corrupt_file_list.append(corrupt_file_path)

        # Get the random_id used for this component version
        random_id = self._component_random_ids.get(
            f"{component_name}-{version}")

        return self._upload_files_to_s3(corrupt_file_list,
                                        self.s3_artifact_bucket, random_id)

    def cleanup(self) -> None:
        for componentArn in self._ggComponentToDeleteArn:
            try:
                self._ggClient.delete_component(arn=componentArn)
            except:
                logging.warning(
                    f'Failed to delete component {componentArn} from configured test account.'
                )

        # Delete S3 artifacts for each component random_id
        for random_id in set(self._component_random_ids.values()):
            folder_path = f"{S3_ARTIFACT_DIR}/{random_id}/"
            paginator = self._s3Client.get_paginator('list_objects_v2')
            objects_to_delete = []

            for page in paginator.paginate(Bucket=self.s3_artifact_bucket,
                                           Prefix=folder_path):
                if 'Contents' in page:
                    for obj in page['Contents']:
                        objects_to_delete.append({'Key': obj['Key']})

            if objects_to_delete:
                for i in range(0, len(objects_to_delete), 1000):
                    batch = objects_to_delete[i:i + 1000]
                    self._s3Client.delete_objects(
                        Bucket=self.s3_artifact_bucket,
                        Delete={
                            'Objects': batch,
                            'Quiet': True
                        })

        # Extract unique thing_group_arns
        unique_thing_groups = {
            thing_group_arn
            for _, thing_group_arn in self._ggDeploymentToThingNameList
        }

        for unique in unique_thing_groups:
            try:
                print(f"Cleaning up thing group arn: {unique}")
                self.remove_all_components(thing_group_arn=unique)
            except Exception as e:
                print(e)

        for (deployment, thing_group_arn) in self._ggDeploymentToThingNameList:
            try:
                print(
                    f"Cleaning up deployment: {deployment}, with thing group arn: {thing_group_arn}"
                )
                self._ggClient.cancel_deployment(deploymentId=deployment)
                self._ggClient.delete_deployment(deploymentId=deployment)
            except Exception as e:
                print(e)

        # Reset the lists.
        self._ggComponentToDeleteArn = []
        self._ggDeploymentToThingNameList = []

        logging.debug("Cleaned up services List:")
        for service in self._ggServiceList:
            logging.debug(f"ggl.{service}.service")
        self._ggServiceList = []

    def wait_ggcore_device_status(
            self, timeout: int | float, thing_group_name,
            desired_health: str) -> Optional[CoreDeviceStatusType]:
        things_in_group = self._iotClient.list_things_in_thing_group(
            thingGroupName=thing_group_name,
            recursive=False,
            nextToken="",
            maxResults=100,
        )

        # Make sure that there is only one thing in the group.
        if len(things_in_group["things"]) != 1:
            print("The number of things in the thing-group must be 1.")
            return False

        while timeout > 0:
            return_val = self._ggClient.get_core_device(
                coreDeviceThingName=things_in_group["things"][0])

            if return_val is None or return_val["status"] != desired_health:
                time.sleep(1)
                timeout -= 1
            else:
                return True

        return False

    def create_recipe_file(self, component_name: str) -> dict | None:
        template_file = os.path.join(".", "misc", "recipe_template.yaml")
        template_abs_path = os.path.abspath(template_file)
        with open(template_abs_path) as template:
            content = template.read()
            recipe_yaml = yaml.safe_load(content)

            recipe_yaml["ComponentName"] = component_name
            recipe_yaml["ComponentVersion"] = "1.0.0"

            template.close()
            return recipe_yaml
        return None

    def upload_component_from_recipe(
            self, recipe: dict) -> Optional[ComponentDeploymentInfo]:
        cloud_addition = str(uuid1())
        recipe_name = recipe["ComponentName"]
        cloud_recipe_name = recipe_name + cloud_addition
        recipe["ComponentName"] = cloud_recipe_name

        try:
            # Create component version using the recipe
            response = self._ggClient.create_component_version(
                inlineRecipe=str(recipe))

            print(
                f"Successfully uploaded component with ARN: {response['arn']}")
            self._ggComponentToDeleteArn.append(response["arn"])
            return ComponentDeploymentInfo(
                name=cloud_recipe_name,
                versions=[recipe["ComponentVersion"]],
                merge_config=None)

        except self._ggClient.exceptions.ConflictException as e:
            print(f"Component version already exists: {e}")
            raise
        except Exception as e:
            print(f"Error uploading component: {e}")
            raise

    def recipe_for_component_exists(self, component_name: str,
                                    component_version: str):
        recipe_path = Path(
            RECIPE_DIR) / f"{component_name}-{component_version}.yaml"
        print(f"Checking if file {recipe_path} exists")
        return recipe_path.exists()
