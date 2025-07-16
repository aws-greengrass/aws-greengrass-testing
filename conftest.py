def pytest_addoption(parser):
    parser.addoption("--commit-id",
                     action="store",
                     default="",
                     help="Commit id")
    parser.addoption("--aws-account",
                     action="store",
                     default="",
                     help="AWS Account ID")
    parser.addoption("--s3-bucket",
                     action="store",
                     default="",
                     help="S3 Bucket Name")
    parser.addoption("--region",
                     action="store",
                     default="us-west-2",
                     help="AWS Region")
    parser.addoption("--ggl-cli-path",
                     action="store",
                     default="",
                     help="GGL CLI Path")
