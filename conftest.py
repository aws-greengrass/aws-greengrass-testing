from curses.ascii import isdigit
from pytest import UsageError


def aws_account_checker(value):
    try:
        int(value)

        if len(value) != 12:
            raise UsageError("AWS Account ID must be 12 digit long.")
    except ValueError:
        raise UsageError("AWS Account ID must be an integer.")

    return value


def aws_test_bucket_name_checker(value):
    if isinstance(value, str):
        return value
    else:
        raise UsageError("S3 bucket name must be a string.")


def aws_test_region_checker(value):
    if not isinstance(value, str):
        raise UsageError("AWS region name must be a string.")

    components = value.split('-')
    if (len(components) != 3 or len(components[0]) != 2
            or len(components[2]) != 1 or not components[2].isdigit()):
        raise UsageError("AWS region is not in correct format.")

    return value


def pytest_addoption(parser):
    parser.addoption(
        "--ggTestAccount",
        action="store",
        default="",
        help="The AWS Account ID to be used for running the tests.",
        required=True,
        type=aws_account_checker,
    )
    parser.addoption(
        "--ggTestBucket",
        action="store",
        default="",
        help="The S3 test bucket used to store artifacts.",
        required=True,
        type=aws_test_bucket_name_checker,
    )
    parser.addoption(
        "--ggTestRegion",
        action="store",
        default="us-west-2",
        help="The region to be used with AWS account.",
        required=True,
        type=aws_test_region_checker,
    )
    parser.addoption(
        "--ggTestThingGroup",
        action="store",
        default="",
        help="The thing group that the tests will deploy to.",
        required=True,
        type=str,
    )
