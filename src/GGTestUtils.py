import json
import os
from typing import Any, Dict, List, Literal, Optional, Sequence, Tuple
from uuid import uuid1
import boto3
from botocore.exceptions import ClientError
import time
import logging
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
    _ggDeploymentToThingNameList: List[Tuple[str,str]]

    def __init__(self, account: str, bucket: str, region: str, cli_bin_path: str):
        self._region = region
        self._account = account
        self._bucket = bucket
        self._cli_bin_path = cli_bin_path
        self._ggClient = boto3.client("greengrassv2", region_name=self._region)
        self._iotClient = boto3.client("iot", region_name=self._region)
        self._s3Client = boto3.client("s3", region_name=self._region)
        self._ggComponentToDeleteArn = []
        self._ggServiceList = []
        self._ggDeploymentToThingNameList = []

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
                                coreDeviceThingName=thing, maxResults=100, nextToken=next_token)

                        if "nextToken" in statistic:
                            next_token = statistic["nextToken"]
                        else:
                            loop_again = False

                        statistic = statistic["effectiveDeployments"]

                        if statistic:
                            statistics_list.append({thing: statistic})

                except Exception as e:
                    print(f"Error getting statistics for {thing}: {str(e)}")

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
        cli_cmd = ["sudo", self.cli_bin_path, "deploy"]
        if artifacts_dir is not None:
            cli_cmd.extend(["--artifacts-dir", artifacts_dir])
        if recipe_dir is not None:
            cli_cmd.extend(["--recipe-dir", recipe_dir])
        cli_cmd.append(f"--add-component={component_details}")
        process = run(cli_cmd, capture_output=True, text=True)
        if process.returncode == 0:
            print("CLI call to create local deployment succeeded:")
            print(process.stdout)
            return True

        print(
            f"CLI call to create local deployment failed with error code {process.returncode}:"
        )
        print(process.stderr)
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
            self._ggDeploymentToThingNameList.append((result["deploymentId"], thingArn))

        return result

    def remove_component(self, deployment_id:str, component_name_to_remove:str, thing_group_arn) -> Literal['SUCCEEDED', 'FAILED', 'TIMEOUT']:
        # Get deployment details
        deployment_info = self._ggClient.get_deployment(deploymentId=deployment_id)

        # Retrieve the actual component configuration
        components = deployment_info.get("components", {})

        if component_name_to_remove not in components:
            raise ValueError(f"Component '{component_name_to_remove}' not found in deployment '{deployment_id}'.")

        # Remove the component
        del components[component_name_to_remove]
        print(f"Removed component '{component_name_to_remove}' from deployment '{deployment_id}'.")

        # Create a new deployment with the updated components
        new_deployment = self.create_deployment(
            thingArn=thing_group_arn,
            component_list=components,
            deployment_name="FirstDeployment")["deploymentId"]

        print(f"New deployment created: {new_deployment}")

        result = self.wait_for_deployment_till_timeout(120, new_deployment)

        print(f"The removal of component through deployment: {result}")

        return result

    def remove_all_components(self, thing_group_arn:str)-> Literal['SUCCEEDED', 'FAILED', 'TIMEOUT']:
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
                                        return "FAILED"
                                    else:
                                        pass

            time.sleep(1)
            timeout -= 1

        return "TIMEOUT"

    def _upload_files_to_s3(self, files: Sequence[os.PathLike | str],
                            bucket_name: str) -> bool:
        """
        Upload a file to an S3 bucket

        :param file_path: File to upload
        :param bucket_name: Bucket to upload to
        :return: True if file was uploaded, else False
        """

        for file_path in files:
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
        return True

    def _upload_component_to_gg(self,
                                recipe_files: List[os.PathLike | str]) -> str:
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

                modified_content = modified_content.replace(
                    recipe_name, cloud_recipe_name)

                # Parse the modified content as YAML and convert it to JSON.
                recipe_yaml = yaml.safe_load(modified_content)
                recipe_json = json.dumps(recipe_yaml)

                try:
                    # Create component version using the recipe
                    response = self._ggClient.create_component_version(
                        inlineRecipe=recipe_json)

                    print(
                        f"Successfully uploaded component with ARN: {response['arn']}"
                    )
                    self._ggComponentToDeleteArn.append(response["arn"])

                except self._ggClient.exceptions.ConflictException as e:
                    print(f"Component version already exists: {e}")
                    raise
                except Exception as e:
                    print(f"Error uploading component: {e}")
                    raise
        return cloud_recipe_name

    def upload_component_with_version_and_deps(
        self, component_name: str, version: str,
        dependencies: List[Tuple[str,
                                 str]]) -> Optional[ComponentDeploymentInfo]:
        try:
            component_artifact_dir = os.path.join('components', component_name,
                                                  version, 'artifacts')

            artifact_files = os.listdir(component_artifact_dir)
            artifact_files_full_paths = [
                os.path.abspath(os.path.join(component_artifact_dir, file))
                for file in artifact_files
            ]
            self._upload_files_to_s3(artifact_files_full_paths,
                                     self.s3_artifact_bucket)
        except FileNotFoundError:
            print(
                f"No artifact directory found for {component_name}-{version}.")
        except PermissionError:
            print(
                f"Cannot access the directory with artifacts for {component_name}-{version}."
            )
            return None

        try:
            component_recipe_dir = os.path.join('components', component_name,
                                                version, 'recipe')

            recipes = os.listdir(component_recipe_dir)
            recipes_full_paths = [
                os.path.abspath(os.path.join(component_recipe_dir, file))
                for file in recipes
            ]

            if len(recipes_full_paths) != 1:
                print("More than one recipe files found.")
                return None

            with open(recipes_full_paths[0]) as recipe:
                recipe_obj = yaml.safe_load(recipe)
                if "ComponentDependencies" not in recipe_obj:
                    print(
                        "ComponentDependencies section not found in the original recipe."
                    )
                    return None

                for dependency in dependencies:
                    if dependency[0] not in recipe_obj["ComponentDependencies"]:
                        print(
                            f"The dependency {dependency[0]} not found in original recipe."
                        )
                        return None
                    print(recipe_obj["ComponentDependencies"])
                    stored_val = recipe_obj["ComponentDependencies"][
                        dependency[0]]
                    del recipe_obj["ComponentDependencies"][dependency[0]]
                    recipe_obj["ComponentDependencies"][
                        dependency[1]] = stored_val

                # Ensure the output directory exists
                output_dir = os.path.join(".", "ggtest", "modified_recipes")
                os.makedirs(output_dir, exist_ok=True)

                new_file_path = os.path.join(
                    output_dir, os.path.basename(recipes_full_paths[0]))

                print(yaml.safe_dump(recipe_obj))

                # Read the original file and write the corrupted version
                with open(new_file_path, "w") as f_out:
                    f_out.write(yaml.safe_dump(recipe_obj))
                    f_out.close()

                cloud_name = self._upload_component_to_gg([new_file_path])

                recipe.close()

                return ComponentDeploymentInfo(name=cloud_name,
                                               versions=[version],
                                               merge_config=None)
        except FileNotFoundError:
            print(f"No recipe directory found for {component_name}-{version}.")
            return None
        except PermissionError:
            print(
                f"Cannot access the directory with recipe for {component_name}-{version}."
            )
            return None

    def upload_component_with_versions(
            self, component_name: str,
            versions: List[str]) -> Optional[ComponentDeploymentInfo]:

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
                                         self.s3_artifact_bucket)
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

                recipes_file_list.append(recipes_full_paths[0])

            print(recipes_file_list)
            cloud_name = self._upload_component_to_gg(recipes_file_list)
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
            output_dir = os.path.join(".", "ggtest", "corruptFiles")
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

        return self._upload_files_to_s3(corrupt_file_list,
                                        self.s3_artifact_bucket)

    def cleanup(self) -> None:
        for componentArn in self._ggComponentToDeleteArn:
            try:
                self._ggClient.delete_component(arn=componentArn)
            except:
                logging.warning(
                    f'Failed to delete component {componentArn} from configured test account.'
                )

        folder_path = S3_ARTIFACT_DIR + "/"
        # List all objects within the folder
        paginator = self._s3Client.get_paginator('list_objects_v2')
        objects_to_delete = []

        for page in paginator.paginate(Bucket=self.s3_artifact_bucket, Prefix=folder_path):
            if 'Contents' in page:
                for obj in page['Contents']:
                    objects_to_delete.append({'Key': obj['Key']})

        if objects_to_delete:
            # Delete objects in batches of 1000 (S3 limit)
            for i in range(0, len(objects_to_delete), 1000):
                batch = objects_to_delete[i:i+1000]
                self._s3Client.delete_objects(
                    Bucket=self.s3_artifact_bucket,
                    Delete={
                        'Objects': batch,
                        'Quiet': True
                    }
                )

        # Extract unique thing_group_arns
        unique_thing_groups = {thing_group_arn for _, thing_group_arn in self._ggDeploymentToThingNameList}

        for unique in unique_thing_groups:
            try:
                print(f"Cleaning up thing group arn: {unique}")
                self.remove_all_components(thing_group_arn=unique)
            except Exception as e:
                print(e)

        for (deployment,thing_group_arn) in self._ggDeploymentToThingNameList:
            try:
                print(f"Cleaning up deployment: {deployment}, with thing group arn: {thing_group_arn}")
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

    def recipe_for_component_exists(self, component_name: str, component_version: str):
        print(f"Checking if file {(Path(RECIPE_DIR) / f"{component_name}-{component_version}.yaml")} exists")
        return (Path(RECIPE_DIR) / f"{component_name}-{component_version}.yaml").exists()
