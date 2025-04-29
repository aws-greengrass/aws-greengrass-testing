from pytest import UsageError
from config import config


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


aws_account_checker(config.aws_account)
aws_test_bucket_name_checker(config.s3_bucket_name)
aws_test_region_checker(config.region)
