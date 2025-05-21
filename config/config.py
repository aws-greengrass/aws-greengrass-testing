# The AWS account ID in which the test resource will be created.
aws_account = "891377305071"

# The S3 bucket used to store the artifacts.
s3_bucket_name = "gglite-dev-test-us-east-1"

# The region of the AWS Account used for the tests.
region = "us-east-1"

# First thing group with 'thing' under test added to it.
thing_group_1 = "rawalexe-deployment-test"

# Second thing group with 'thing' under test added to it.
thing_group_2 = "rawalexe-uat-test2"

# Thing which is part of the above thing groups.
thing_name = "rawalDevDevice"

# Location of ggl-cli binary
ggl_cli_bin_path = "../aws-greengrass-lite/build/bin/ggl-cli"

# GGL install directory
ggl_install_dir = "/var/lib/greengrass/"
