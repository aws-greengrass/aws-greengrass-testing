from time import sleep, time
from typing import List, Optional
from uuid import uuid1
from boto3 import client
from types_boto3_iot import IoTClient
import botocore


class IoTTestUtils():
    _aws_account: str
    _region: str
    _iotClient: IoTClient
    _thing_groups_to_clean_up: List[str]

    def __init__(self, account: str, region: str) -> None:
        self._aws_account = account
        self._region = region
        self._iotClient = client("iot", region_name=self._region)
        self._thing_groups_to_clean_up = []

    def cleanup(self) -> None:
        for thing_group in self._thing_groups_to_clean_up:
            self._iotClient.delete_thing_group(thingGroupName=thing_group)

    def create_new_thing_group(self, thing_group_name: str) -> str | None:
        garbled_name = thing_group_name + str(uuid1())
        response = self._iotClient.create_thing_group(
            thingGroupName=garbled_name)

        if response is not None:
            self._thing_groups_to_clean_up.append(response['thingGroupName'])
            return response['thingGroupName']

        return None

    def thing_group_exists(self, thing_group: str) -> bool:
        try:
            result = self._iotClient.describe_thing_group(
                thingGroupName=thing_group)
            return ('thingGroupName'
                    in result) and (thing_group == result['thingGroupName'])
        except botocore.exceptions.ClientError as error:
            if error.response['Error']['Code'] == 'ResourceNotFoundException':
                return False
            else:
                raise error

    def add_thing_to_thing_group(self, thing: str,
                                 thing_group: str) -> Optional[str]:
        thing_group_local = thing_group
        if self.thing_group_exists(thing_group_local) is not True:
            thing_group_local = self.create_new_thing_group(thing_group_local)
            if thing_group_local is None:
                return None

            # Wait for the thing group to show up.
            end_time = time() + 60
            while time() < end_time:
                if self.thing_group_exists(
                        thing_group=thing_group_local) is True:
                    break
                sleep(1)

        self._iotClient.add_thing_to_thing_group(
            thingGroupName=thing_group_local, thingName=thing)

        # Try to see whether the thing got added to the thing group.
        end_time = time() + 60
        while time() < end_time:
            if self.is_thing_in_thing_groups(thing,
                                             [thing_group_local]) is True:
                return thing_group_local
            sleep(1)

        return None

    def remove_thing_from_thing_group(self, thing: str,
                                      thing_group: str) -> bool | None:
        if self.thing_group_exists(thing_group) is not True:
            print("Thing group does not exist.")
            return False

        self._iotClient.remove_thing_from_thing_group(
            thingGroupName=thing_group, thingName=thing)

        # Try to see whether the thing got removed from the thing group.
        end_time = time() + 60
        while time() < end_time:
            if self.is_thing_in_thing_groups(thing, [thing_group]) is not True:
                return True
            sleep(1)

        return False

    def is_thing_in_thing_groups(self, thing: str,
                                 thing_groups: List[str]) -> bool:
        for group in thing_groups:
            token = None
            continue_loop = True
            things_list = []

            while continue_loop is True:
                if token is None:
                    response = self._iotClient.list_things_in_thing_group(
                        thingGroupName=group, maxResults=100)
                else:
                    response = self._iotClient.list_things_in_thing_group(
                        thingGroupName=group, maxResults=100, nextToken=token)

                # If the response is invalid or there are no things in the thing
                # group, no point in checking further.
                if response is None or 'things' not in response:
                    return False

                things_list.extend(response['things'])

                if 'nextToken' not in response:
                    continue_loop = False
                else:
                    token = response['nextToken']

            if thing in things_list:
                continue
            else:
                return False

        return True
