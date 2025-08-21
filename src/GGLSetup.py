import argparse
import grp
import pwd
import json
import os
import re
import uuid
import shutil
import subprocess
import platform
import requests
import zipfile
from typing import Any, Dict, List, Literal, Optional, Sequence, Tuple
from uuid import uuid1
from boto3 import client
import time
import logging
import yaml
from subprocess import run
from pathlib import Path
from typing import Sequence, Optional, Any, Dict, List, Literal, Optional, Sequence, NamedTuple

S3_ARTIFACT_DIR = "artifacts"
DEVICE_PATH = "/var/lib/greengrass/device.pem.crt"
PRIVATE_PATH = "/var/lib/greengrass/private.pem.key"
CA_PATH = "/var/lib/greengrass/AmazonRootCA1.pem"
JSON_FILE = "iot_setup_data.json"


def install_greengrass_lite_from_source(commit_id: str, region: str):
    # Get config
    with open(JSON_FILE, 'r') as file:
        data = json.load(file)

    device_cert = data['DEVICE_CERT']
    private_key = data['PRIVATE_KEY']
    thing_name = data['THING_NAME']

    # Download the source repo
    download_result = _download_source(commit_id)
    if not download_result:
        return False

    # Change the working dir
    original_dir = os.getcwd()
    os.chdir("aws-greengrass-lite")

    # Set up an iot client
    iot_client = client("iot", region_name=region)

    try:

        # Install build tools
        install_result = _install_build_dependencies()
        if not install_result:
            return False

        # Add user and group
        add_result_ggcore = _add_user_and_group("ggcore", "ggcore")
        add_result_gg_component = _add_user_and_group("gg_component",
                                                      "gg_component")
        if not add_result_ggcore or not add_result_gg_component:
            return False

        # Create several directories
        dir1_result = _create_dir("/ggcredentials",
                                  ownership=True,
                                  flag='-R',
                                  user="ggcore",
                                  group="ggcore")
        dir2_result = _create_dir("/var/lib/greengrass",
                                  ownership=True,
                                  user="ggcore",
                                  group="ggcore")
        dir3_result = _create_dir("/etc/greengrass")
        if not dir1_result or not dir2_result or not dir3_result:
            return False

        # TES setup
        tes_result = _tes_setup(device_cert, private_key)
        if not tes_result:
            return False

        # Config setup
        src_path = "docs/examples/sample_nucleus_config.yaml"
        temp_path = "./config.yaml"
        dest_path = "/etc/greengrass/config.yaml"

        move_result1 = _copy_file(src_path, temp_path)
        config_result = _modify_config(iot_client, thing_name, temp_path,
                                       "ggcore", "ggcore")
        move_result2 = _copy_file(temp_path, dest_path)
        remove_result = _remove_file(temp_path)
        if not config_result or not move_result1 or not move_result2 or not remove_result:
            return False

        # Build
        build_result = _build_with_cmake()
        if not build_result:
            return False

        # run nucleus
        os.chmod('misc/run_nucleus', 0o775)
        process = subprocess.run(['sudo', './misc/run_nucleus'],
                                 check=True,
                                 text=True,
                                 stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE)
        print("Successfully installed the nucleus from source")

    except Exception as e:
        print(f"Unexpected error: {e}")
    finally:
        os.chdir(original_dir)


def clean_up() -> bool:
    # Stop and disable services
    stop_result = _stop_and_disable_services()
    if not stop_result:
        return False

    # Remove aws-greengrass-lite
    remove_ggl = _remove_dir("aws-greengrass-lite")
    if not remove_ggl:
        return False

    # Remove several other directories
    remove_dir1 = _remove_dir("/ggcredentials")
    remove_dir2 = _remove_dir("/var/lib/greengrass")
    remove_dir3 = _remove_dir("/etc/greengrass")
    if not remove_dir1 or not remove_dir2 or not remove_dir3:
        return False

    # Delete user and group
    delete_result_ggcore = _delete_user_and_group("ggcore", "ggcore")
    delete_result_gg_component = _delete_user_and_group("gg_component",
                                                        "gg_component")
    if not delete_result_ggcore or not delete_result_gg_component:
        return False

    print("Successfully cleaned up greengrass-lite")
    return True


# ===============================================
# HELPER FUNCTIONS
# ===============================================
def _download_source(commit_id: str, max_retries=3) -> bool:
    url = f"https://github.com/aws-greengrass/aws-greengrass-lite/archive/{commit_id}.zip"
    src_path = "./aws-greengrass-lite.zip"
    extracted_folder = f"aws-greengrass-lite-{commit_id}"
    target_folder = "aws-greengrass-lite"

    if os.path.isdir(target_folder):
        print("Please clean up before downloading the aws-greengrass-lite")
        return False

    for attempt in range(max_retries):
        try:
            response = requests.get(url)

            if response.status_code != 200:
                print(
                    f"Failed to download aws-greengrass-lite, status code: {response.status_code}"
                )
                if attempt < max_retries - 1:
                    continue
                return False

            with open(src_path, 'wb') as f:
                f.write(response.content)

            if _unzip_file(src_path, '.'):
                os.rename(extracted_folder, target_folder)
                print("Successfully downloaded aws-greengrass-lite")
                return True

        except requests.exceptions.RequestException as e:
            print(f"Error when downloading aws-greengrass-lite, {str(e)}")
            if attempt == max_retries - 1:
                print(
                    f"Failed to download aws-greengrass-lite after {max_retries} attempts"
                )
                return False


def _unzip_file(src_path: str, dest_path: str) -> bool:
    try:
        with zipfile.ZipFile(src_path, 'r') as zip_ref:
            zip_ref.extractall(dest_path)

        os.remove(src_path)
        return True

    except Exception as e:
        print(f"Error during extraction: {str(e)}")
        return False


def _install_build_dependencies() -> bool:
    try:
        # List of packages to install
        packages = [
            "build-essential", "pkg-config", "cmake", "git", "curl",
            "libssl-dev", "libcurl4-openssl-dev", "uuid-dev", "libzip-dev",
            "libsqlite3-dev", "libyaml-dev", "libsystemd-dev", "libevent-dev",
            "liburiparser-dev", "cgroup-tools"
        ]

        # Update package list
        update_command = ["sudo", "apt", "update"]
        subprocess.run(update_command, check=True)

        # Install packages
        install_command = ["sudo", "apt", "install", "-y"] + packages
        subprocess.run(install_command, check=True)

        print("Successfully updated and installed dependencies")
        return True

    except Exception as e:
        print(f"Error when installing dependencies: {e}", file=sys.stderr)
        return False


def _add_user_and_group(user: str, group: str):
    try:
        # Create the group
        if not _check_group_exists(group):
            subprocess.run(['sudo', 'groupadd', group], check=True)
            print(f"Successfully created {group} group")

        # Create the user and add to the group
        if not _check_user_exists(user):
            subprocess.run(['sudo', 'useradd', '-Ng', group, user], check=True)
            print(f"Successfully created {user} user")
        return True

    except subprocess.CalledProcessError as e:
        print(f"Command failed: {e.cmd}. Return code: {e.returncode}")
        return False
    except Exception as e:
        print(f"Error when adding user and group: {str(e)}")
        return False


def _delete_user_and_group(user: str, group: str):
    try:
        # Delete the user
        if _check_user_exists(user):
            subprocess.run(['sudo', 'userdel', user], check=True)
            print(f"Successfully deleted {user} user")

        # Delete the group
        if _check_group_exists(group):
            subprocess.run(['sudo', 'groupdel', group], check=True)
            print(f"Successfully deleted {group} group")
        return True

    except subprocess.CalledProcessError as e:
        print(f"Command failed: {e.cmd}. Return code: {e.returncode}")
        return False
    except Exception as e:
        print(f"Error when deleting user and group: {str(e)}")
        return False


def _check_group_exists(group: str) -> bool:
    try:
        grp.getgrnam(group)
        return True
    except KeyError:
        return False


def _check_user_exists(user: str) -> bool:
    try:
        pwd.getpwnam(user)
        return True
    except KeyError:
        return False


def _build_with_cmake() -> bool:
    original_dir = os.getcwd()
    try:
        # CMake configure command
        cmake_cmd = [
            'cmake', '-B', 'build', '-D', 'CMAKE_INSTALL_PREFIX=/usr/local',
            '-D', 'CMAKE_BUILD_TYPE=MinSizeRel', '-D', 'CMAKE_BUILD_TYPE=Debug',
            '-DGGL_LOG_LEVEL=DEBUG'
        ]

        subprocess.run(cmake_cmd, check=True)

        # Change the directory to build
        os.chdir("build")
        build_cmd = ['make', '-C', 'build', '-j$(nproc)']
        subprocess.run(build_cmd, shell=True, check=True)
        os.chdir(original_dir)

        # Install command
        install_cmd = ['sudo', 'make', '-C', 'build', 'install']
        subprocess.run(install_cmd, check=True)

        print("Successfully completed the build process")
        return True

    except subprocess.CalledProcessError as e:
        print(f"Error during build process: {e}")
        return False
    except Exception as e:
        print(f"Unexpected error: {e}")
        return False
    finally:
        os.chdir(original_dir)


def _tes_setup(device_cert: str, private_key: str) -> bool:

    # Save the key and certificates
    try:
        device_result = _create_file(DEVICE_PATH, device_cert)
        key_result = _create_file(PRIVATE_PATH, private_key)

        if not device_result or not key_result:
            return False

        root_ca_url = "https://www.amazontrust.com/repository/AmazonRootCA1.pem"
        ca_response = requests.get(root_ca_url)
        ca_result = _create_file(CA_PATH, ca_response.text)
        if not ca_result:
            return False

        print("Successfully saved all required certificates and keys")
        return True

    except Exception as e:
        print(f"Error when writing certificate or key: {str(e)}")
        return False


def _modify_config(iot_client: client, thing_name: str, file_path: str,
                   group: str, user: str) -> bool:

    try:
        iot_data_endpoint = iot_client.describe_endpoint(
            endpointType='iot:Data-ATS')['endpointAddress']
        iot_cred_endpoint = iot_client.describe_endpoint(
            endpointType='iot:CredentialProvider')['endpointAddress']

        with open(file_path, 'r') as file:
            data = yaml.safe_load(file)

        data['system']['thingName'] = thing_name
        data['system']['privateKeyPath'] = PRIVATE_PATH
        data['system']['certificateFilePath'] = DEVICE_PATH
        data['system']['rootCaPath'] = CA_PATH
        data['services']['aws.greengrass.NucleusLite']['configuration'][
            'iotCredEndpoint'] = iot_cred_endpoint
        data['services']['aws.greengrass.NucleusLite']['configuration'][
            'iotDataEndpoint'] = iot_data_endpoint
        data['services']['aws.greengrass.NucleusLite']['configuration'][
            'runWithDefault']['posixUser'] = group + ':' + user
        data['services']['aws.greengrass.NucleusLite']['configuration'][
            'iotRoleAlias'] = "ggl-uat-role-alias"
        print(data)

        with open(file_path, 'w') as file:
            yaml.dump(data, file, default_flow_style=False)

        print(f"Successfully modified the config file")
        return True

    except Exception as e:
        print(f"Error when modifying config.yaml: {str(e)}")
        return False


def _stop_and_disable_services():
    commands = [
        ['sudo', 'systemctl', 'stop', 'greengrass-lite.target'],
        ['sudo', 'systemctl', 'disable', 'greengrass-lite.target'],
        ['sudo', 'systemctl', 'disable', 'ggl.aws_iot_tes.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.aws_iot_mqtt.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg_config.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg_health.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg_fleet_status.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg_deployment.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg_pubsub.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.gg-ipc.socket.socket'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.ggconfigd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.iotcored.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.tesd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.ggdeploymentd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.gg-fleet-statusd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.ggpubsubd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.gghealthd.service'],
        ['sudo', 'systemctl', 'disable', 'ggl.core.ggipcd.service'],
        ['sudo', 'systemctl', 'daemon-reload'],
        ['sudo', 'rm', '-rf', '/etc/systemd/system/ggl*']
    ]

    for cmd in commands:
        try:
            subprocess.run(cmd, check=True)
        except Exception as e:
            print(f"Error when executing {' '.join(cmd)}: {e}")
            return False
    print("Successfully stopped and disabled all services")
    return True


# ===============================================
# FILE & DIR FUNCTIONS
# ===============================================


def _create_dir(dir: str,
                permission=False,
                octal=None,
                ownership=False,
                flag=None,
                user=None,
                group=None) -> bool:
    try:
        if not os.path.exists(dir):
            subprocess.run(['sudo', 'mkdir', dir], check=True)
        if permission and octal is not None:
            subprocess.run(['sudo', 'chmod', octal, dir], check=True)
        if ownership and user is not None and group is not None:
            if flag is not None:
                subprocess.run(['sudo', 'chown', flag, f'{user}:{group}', dir],
                               check=True)
            else:
                subprocess.run(['sudo', 'chown', f'{user}:{group}', dir],
                               check=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Error when creating directory: {str(e)}")
        return False


def _remove_dir(dir: str) -> bool:
    try:
        if os.path.exists(dir):
            subprocess.run(['sudo', 'rm', '-rf', dir], check=True)
            print(f"Successfully removed {dir}")
            return True
        else:
            print(f"Error when removing directory: {dir} does not exist")
            return False
    except subprocess.CalledProcessError as e:
        print(f"Error when removing directory: {str(e)}")
        return False


def _create_file(file_path: str, content="") -> bool:
    try:
        subprocess.run(['sudo', 'tee', file_path],
                       input=content,
                       check=True,
                       text=True,
                       stdout=subprocess.DEVNULL)
        return True
    except Exception as e:
        print(f"Error when creating file and changing permission: {str(e)}")
        return False


def _move_file(src_path: str, dest_path: str) -> bool:
    try:
        subprocess.run(['sudo', 'mv', src_path, dest_path], check=True)
        return True
    except Exception as e:
        print(f"Error when moving {src_path} to {dest_path}: {str(e)}")
        return False


def _copy_file(src_path: str, dest_path: str) -> bool:
    try:
        subprocess.run(['sudo', 'cp', '-p', src_path, dest_path], check=True)
        return True
    except Exception as e:
        print(f"Error when copying {src_path} to {dest_path}: {str(e)}")
        return False


def _remove_file(file_path: str) -> bool:
    try:
        if os.path.exists(file_path):
            subprocess.run(['sudo', 'rm', '-rf', file_path], check=True)
            return True
        else:
            print("Error when removing the file: file does not exist")
            return False
    except subprocess.CalledProcessError as e:
        print(f"Error when removing the file: {str(e)}")
        return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest='function',
                                       help='Function to help install')

    # Parser for install_greengrass_lite_from_source
    source_parser = subparsers.add_parser('install_greengrass_lite_from_source')
    source_parser.add_argument('--id', required=True, help='Commit id')
    source_parser.add_argument('--region', required=True, help='AWS region')

    # Parser for clean_up without arguments
    subparsers.add_parser('clean_up')

    args = parser.parse_args()

    # Call the selected function with appropriate arguments
    if args.function == 'install_greengrass_lite_from_source':
        install_greengrass_lite_from_source(args.id, args.region)
    elif args.function == 'clean_up':
        clean_up()
