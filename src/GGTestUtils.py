import json
import os
import uuid
import boto3
from botocore.exceptions import ClientError
import time
import yaml
from subprocess import run

S3_ARTIFACT_DIR = "artifacts"


class GGTestUtils:

    def __init__(self, account, bucket, region, cli_bin_path):
        self._region = region
        self._account = account
        self._bucket = bucket
        self._cli_bin_path = cli_bin_path
        self._ggClient = boto3.client("greengrassv2", region_name=self._region)
        self._iotClient = boto3.client("iot", region_name=self._region)
        self._s3Client = boto3.client("s3", region_name=self._region)
        self._ggComponentToDeleteArn = []
        self._ggS3ObjToDelete = []
        self._ggServiceList = []

    def get_aws_account(self):
        return self._account

    def get_s3_artifact_bucket(self):
        return self._bucket

    def get_region(self):
        return self._region

    def get_cli_bin_path(self):
        return self._cli_bin_path

    def get_thing_group_arn(self, thing_group):
        return f"arn:aws:iot:{self.get_region()}:{self.get_aws_account()}:thinggroup/{thing_group}"

    def _get_things_in_thing_group(self, thing_group_name):
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

    def _check_greengrass_group_deployment_status(self, deployment_id):
        """
        Check the status of a Greengrass deployment for a thing group.

        :param deployment_id: The ID of the deployment to check
        :return: A dictionary containing the deployment status and details
        """
        try:
            # Get the deployment status
            response = self._ggClient.get_deployment(
                deploymentId=deployment_id)

            # Extract relevant information
            deployment_status = response["deploymentStatus"]
            target_arn = response["targetArn"]
            creation_timestamp = response["creationTimestamp"]

            things_list = self._get_things_in_thing_group(
                target_arn.split("/")[-1])

            statistics_list = []

            for thing in things_list:
                try:
                    # Get deployment statistics
                    statistic = self._ggClient.list_effective_deployments(
                        coreDeviceThingName=thing)["effectiveDeployments"]
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

    def create_local_deployment(self, artifacts_dir, recipe_dir, component_details):
        cli_cmd = ["sudo", self.get_cli_bin_path(), "deploy"]
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

        print(f"CLI call to create local deployment failed with error code {process.returncode}:")
        print(process.stderr)
        return False

    def create_deployment(self,
                          thingArn,
                          component_list,
                          deployment_name="UATinPython"):
        component_parsed_dict = {}
        for component in component_list:
            component_parsed_dict[component[0]] = {
                "componentVersion": component[1]
            }

        result = self._ggClient.create_deployment(
            targetArn=thingArn,
            deploymentName=deployment_name,
            components=component_parsed_dict,
        )

        if result is not None:
            self._ggServiceList.extend(
                [component[0] for component in component_list])

        return result

    def wait_for_deployment_till_timeout(self, timeout, deployment_id) -> str:
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

    def _upload_files_to_s3(self, files, bucket_name):
        """
        Upload a file to an S3 bucket

        :param file_path: File to upload
        :param bucket_name: Bucket to upload to
        :return: True if file was uploaded, else False
        """

        for file_path in files:
            object_name = S3_ARTIFACT_DIR + "/" + os.path.basename(file_path)

            try:
                # Upload the file
                response = self._s3Client.upload_file(file_path, bucket_name,
                                                      object_name)
                self._ggS3ObjToDelete.append(object_name)
            except Exception as e:
                print(f"Error uploading file: {e}")
                return False

            print(
                f"File {file_path} successfully uploaded to {bucket_name}/{object_name}"
            )
        return True

    def _upload_component_to_gg(self, recipe):
        recipe_name = ""
        recipe_content = ""
        cloud_addition = str(uuid.uuid1())

        # Read and modify the file
        with open(recipe, "r") as f:
            recipe_content = f.read()
            recipe_name = yaml.safe_load(recipe_content)["ComponentName"]

            cloud_recipe_name = recipe_name + cloud_addition
            modified_content = recipe_content.replace(
                "$bucketName$", self.get_s3_artifact_bucket())
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
                return cloud_recipe_name

            except self._ggClient.exceptions.ConflictException as e:
                print(f"Component version already exists: {e}")
                raise
            except Exception as e:
                print(f"Error uploading component: {e}")
                raise

    def upload_component_with_version(self, component_name, version):
        try:
            component_artifact_dir = "./components/" + component_name + "/" + version + "/artifacts/"

            artifact_files = os.listdir(component_artifact_dir)
            artifact_files_full_paths = [
                os.path.abspath(os.path.join(component_artifact_dir, file))
                for file in artifact_files
            ]
            self._upload_files_to_s3(artifact_files_full_paths,
                                     self.get_s3_artifact_bucket())
        except FileNotFoundError:
            print(
                f"No artifact directory found for {component_name}-{version}.")
        except PermissionError:
            print(
                f"Cannot access the directory with artifacts for {component_name}-{version}."
            )
            return None

        try:
            component_recipe_dir = "./components/" + component_name + "/" + version + "/recipe/"

            recipes = os.listdir(component_recipe_dir)
            recipes_full_paths = [
                os.path.abspath(os.path.join(component_recipe_dir, file))
                for file in recipes
            ]

            return (self._upload_component_to_gg(recipes_full_paths[0]),
                    version)
        except FileNotFoundError:
            print(f"No recipe directory found for {component_name}-{version}.")
            return None
        except PermissionError:
            print(
                f"Cannot access the directory with recipe for {component_name}-{version}."
            )
            return None

    def _create_corrupt_file(self, file_path):
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

    def upload_corrupt_artifacts_to_s3(self, component_name, version):
        component_artifact_dir = "./components/" + component_name + "/" + version + "/artifacts/"

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
                                        self.get_s3_artifact_bucket())

    def cleanup(self):
        for componentArn in self._ggComponentToDeleteArn:
            self._ggClient.delete_component(arn=componentArn)
        for artifact in self._ggS3ObjToDelete:
            self._s3Client.delete_object(Bucket=self.get_s3_artifact_bucket(),
                                         Key=artifact)

        # Reset the lists.
        self._ggComponentToDeleteArn = []
        self._ggS3ObjToDelete = []

        for service in self._ggServiceList:
            print(f"ggl.{service}.service")
        self._ggServiceList = []

    def get_ggcore_device_status(self, timeout, thing_group_name):
        things_in_group = self._iotClient.list_things_in_thing_group(
            thingGroupName=thing_group_name,
            recursive=False,
            nextToken="",
            maxResults=100,
        )

        # Make sure that there is only one thing in the group.
        assert len(things_in_group["things"]) == 1

        while timeout > 0:
            return_val = self._ggClient.get_core_device(
                coreDeviceThingName=things_in_group["things"][0])

            if return_val is None or return_val["status"] != "HEALTHY":
                time.sleep(1)
                timeout -= 1
            else:
                return return_val["status"]

        return None
