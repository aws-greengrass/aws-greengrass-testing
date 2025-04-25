import json
import os
import subprocess
import uuid
import boto3
from botocore.exceptions import ClientError
import time
import yaml
import pytest

S3_ARTIFACT_DIR = "artifacts"


class SystemInterface:

    def __init__(self):
        pass

    def monitor_journalctl_for_message(self, service_name, message, timeout):
        try:
            cmd = [
                "sudo",
                "journalctl",
                "-xeau",
                service_name,
                "-f",  # Follow mode - shows new entries as they are added
            ]

            # Run the command and stream output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True,
            )

            print(f"Monitoring logs for {service_name}...")
            timeout = time.time() + timeout

            while True:
                # Check timeout
                if time.time() > timeout:
                    print(f"Timeout after {timeout} seconds")
                    process.terminate()
                    return False

                output = process.stdout.readline()
                if output:
                    if message in output.strip():
                        print(f"Found log")
                        return True

                # Check if process has terminated
                if process.poll() is not None:
                    print("journalctl process killed.")
                    return False

                time.sleep(0.01)

        except KeyboardInterrupt:
            print("\nStopping monitor...")
            process.terminate()
            return False
        except Exception as e:
            print(f"Error: {e}")
            return False
        finally:
            # Ensure process is terminated
            try:
                process.terminate()
            except:
                pass


class GGTestUtils:

    def __init__(self, account, bucket, region, thing_group):
        self._region = region
        self._account = account
        self._bucket = bucket
        self._thing_group = thing_group
        self._ggClient = boto3.client("greengrassv2", region_name=self._region)
        self._iotClient = boto3.client("iot", region_name=self._region)
        self._s3Client = boto3.client("s3", region_name=self._region)
        self._ggComponentToDeleteArn = []
        self._ggS3ObjToDelete = []
        self._ggServiceList = []

    def _get_things_in_thing_group(self, thing_group_name):
        """
        Retrieves a list of things in a given thing group.

        Args:
            thing_group_name (str): The name of the thing group.

        Returns:
            list: A list of thing names in the thing group, or None if an error occurs.
        """
        client = boto3.client("iot", region_name=self._region)
        try:
            response = client.list_things_in_thing_group(
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

    def create_deployment(self,
                          thingArn,
                          component_name,
                          component_version,
                          deployment_name="UATinPython"):
        result = self._ggClient.create_deployment(
            targetArn=thingArn,
            deploymentName=deployment_name,
            components={
                component_name: {
                    "componentVersion": component_version
                }
            },
        )

        if result is not None:
            self._ggServiceList.append(component_name)

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
            modified_content = recipe_content.replace("$bucketName$",
                                                      self._bucket)
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
        component_artifact_dir = "./" + component_name + "/" + version + "/artifacts/"
        artifact_files = os.listdir(component_artifact_dir)
        artifact_files_full_paths = [
            os.path.abspath(os.path.join(component_artifact_dir, file))
            for file in artifact_files
        ]
        self._upload_files_to_s3(artifact_files_full_paths, self._bucket)

        component_recipe_dir = "./" + component_name + "/" + version + "/recipe/"
        recipes = os.listdir(component_recipe_dir)
        recipes_full_paths = [
            os.path.abspath(os.path.join(component_recipe_dir, file))
            for file in recipes
        ]
        print(recipes_full_paths)
        return self._upload_component_to_gg(recipes_full_paths[0])

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
        component_artifact_dir = "./" + component_name + "/" + version + "/artifacts/"
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

        return self._upload_files_to_s3(corrupt_file_list, self._bucket)

    def cleanup(self):
        for componentArn in self._ggComponentToDeleteArn:
            self._ggClient.delete_component(arn=componentArn)
        for artifact in self._ggS3ObjToDelete:
            self._s3Client.delete_object(Bucket=self._bucket, Key=artifact)

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

            if return_val == None or return_val["status"] != "HEALTHY":
                time.sleep(1)
                timeout -= 1
            else:
                return return_val["status"]

        return None


@pytest.fixture(scope="function")  # Runs for each test function
def gg_util_obj(pytestconfig):
    # Setup an instance of the GGUtils class. It is then passed to the
    # test functions.
    gg_util = GGTestUtils(
        pytestconfig.getoption("ggTestAccount"),
        pytestconfig.getoption("ggTestBucket"),
        pytestconfig.getoption("ggTestRegion"),
        pytestconfig.getoption("ggTestThingGroup"),
    )

    # yield the instance of the class to the tests.
    yield gg_util

    # This section is called AFTER the test is run.

    # Cleanup the artifacts, components etc.
    gg_util.cleanup()


@pytest.fixture(scope="function")  # Runs for each test function
def system_interface(pytestconfig):
    interface = SystemInterface()

    # yield the instance of the class to the tests.
    yield interface

    # This section is called AFTER the test is run.
    pass


# As a component developer, I can create Greengrass component that works on my current platform.
def test_Component_12_T1(gg_util_obj, system_interface):
    # I upload component "MultiPlatform" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "MultiPlatform", "1.0.0")

    # And  I create a deployment configuration with components and configuration
    #   | MultiPlatform | 1.0.0 |
    # And   I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        f"arn:aws:iot:{gg_util_obj._region}:{gg_util_obj._account}:thinggroup/{gg_util_obj._thing_group}",
        component_cloud_name,
        "1.0.0",
    )["deploymentId"]

    # Then the deployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And  I can check the cli to see the status of component MultiPlatform is RUNNING
    """ GG LITE CLI DOESN"T SUPPORT THIS YET. """

    # And  the MultiPlatform log eventually contains the line "Hello world!" within 20 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name + ".service",
        "Hello world! World",
        timeout=20) == True)


# GC developer can create a component with recipes containing s3 artifact. GGC operator can deploy it and artifact can be run.
def test_Component_16_T1(gg_util_obj, system_interface):
    # I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        f"arn:aws:iot:{gg_util_obj._region}:{gg_util_obj._account}:thinggroup/{gg_util_obj._thing_group}",
        component_cloud_name,
        "1.0.0",
    )["deploymentId"]

    # Then the deployment completes with SUCCEEDED within 120 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        120, deployment_id) == "SUCCEEDED")

    # Then I can check the cli to see the status of component HelloWorld is RUNNING
    """ GG LITE CLI DOESN"T SUPPORT THIS YET. """

    # Then the HelloWorld log contains the line "Evergreen's dev experience is great!"
    assert (system_interface.monitor_journalctl_for_message(
        "ggl." + component_cloud_name + ".service",
        "Evergreen's dev experience is great!",
        timeout=20,
    ) == True)


# As a component developer, I expect kernel to fail the deployment if the checksum of downloaded artifacts does not match with the checksum in the recipe.
def test_Component_27_T1(gg_util_obj, system_interface):
    # Given I upload component "HelloWorld" version "1.0.0" from the local store
    # And I ensure component "HelloWorld" version "1.0.0" exists on cloud within 120 seconds
    # And kernel registered as a Thing
    # And my device is running the evergreen-kernel
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I corrupt the contents of the component HelloWorld version 1.0.0 in the S3 bucket
    assert gg_util_obj.upload_corrupt_artifacts_to_s3("HelloWorld",
                                                      "1.0.0") == True

    # When I create a deployment configuration with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the deployment configuration
    deployment_id = gg_util_obj.create_deployment(
        f"arn:aws:iot:{gg_util_obj._region}:{gg_util_obj._account}:thinggroup/{gg_util_obj._thing_group}",
        component_cloud_name,
        "1.0.0",
    )["deploymentId"]

    # Greengrass retries 10 times with a 1 minute interval
    # Then the deployment completes with FAILED within 630 seconds
    assert gg_util_obj.wait_for_deployment_till_timeout(
        630, deployment_id) == "FAILED"

    # the greengrass log eventually contains the line "Failed to verify digest." within 30 seconds
    assert (system_interface.monitor_journalctl_for_message(
        "ggl.core.ggdeploymentd.service",
        "Failed to verify digest.",
        timeout=30,
    ) == True)


# Scenario: FleetStatus-1-T1: As a customer I can get thing information with components whose statuses have changed after an IoT Jobs deployment succeeds
def test_FleetStatus_1_T1(gg_util_obj):
    # When I upload component "HelloWorld" version "1.0.0" from the local store
    component_cloud_name = gg_util_obj.upload_component_with_version(
        "HelloWorld", "1.0.0")

    # Give 5 sec for cloud to calculate artifact checksum and make it "DEPLOYABLE"
    time.sleep(5)

    # When I create a deployment configuration for deployment FirstDeployment and thing group FssThingGroup with components
    #        | HelloWorld | 1.0.0 |
    # And I deploy the configuration for deployment FirstDeployment
    deployment_id = gg_util_obj.create_deployment(
        f"arn:aws:iot:{gg_util_obj._region}:{gg_util_obj._account}:thinggroup/{gg_util_obj._thing_group}",
        component_cloud_name,
        "1.0.0",
        "FirstDeployment",
    )["deploymentId"]

    # Then the deployment FirstDeployment completes with SUCCEEDED within 180 seconds
    assert (gg_util_obj.wait_for_deployment_till_timeout(
        180, deployment_id) == "SUCCEEDED")

    # And I can get the thing status as "HEALTHY" with all uploaded components within 60 seconds with groups
    #      | FssThingGroup |
    assert (gg_util_obj.get_ggcore_device_status(
        60, f"{gg_util_obj._thing_group}") == "HEALTHY")
