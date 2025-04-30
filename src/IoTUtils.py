from time import sleep
from typing import List
from uuid import uuid1
from boto3 import client
from types_boto3_iot import IoTClient


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

    def add_thing_to_thing_group(self, thing: str, thing_group: str) -> bool:
        self._iotClient.add_thing_to_thing_group(thingGroupName=thing_group,
                                                 thingName=thing)

        # Try to see whether the thing got added to the thing group.
        for i in range(3):
            if self.is_thing_in_thing_groups(thing, [thing_group]) is True:
                return True
            sleep(2)

        return False

    def is_thing_in_thing_groups(self, thing: str, thing_groups: list) -> bool:
        for group in thing_groups:
            response = self._iotClient.list_things_in_thing_group(
                thingGroupName=group, maxResults=100)
            if response is None or 'things' not in response:
                return False

            if thing in response['things']:
                continue
            else:
                return False

        return True
