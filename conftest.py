import pytest
import sys

sys.path.insert(0, './src')
from GGLSetup import clean_up


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

    # Smart default for ggl-cli-path
    import shutil
    import os
    default_cli_path = ""
    if shutil.which("ggl-cli"):
        default_cli_path = "ggl-cli"
    elif os.path.exists(
            "/tmp/aws-greengrass-testing-workspace/aws-greengrass-lite/build/bin/ggl-cli"
    ):
        default_cli_path = "/tmp/aws-greengrass-testing-workspace/aws-greengrass-lite/build/bin/ggl-cli"

    parser.addoption("--ggl-cli-path",
                     action="store",
                     default=default_cli_path,
                     help="GGL CLI Path")


@pytest.fixture(autouse=True)
def cleanup_after_test():
    """Cleanup greengrass state after each test to prevent state pollution"""
    yield    # Test runs here
    print("\nCleaning up greengrass state after test...")
    clean_up()

    # Clear systemd journal logs to avoid log pollution between tests
    try:
        import subprocess
        subprocess.run(["journalctl", "--rotate"],
                       check=False,
                       capture_output=True,
                       timeout=5)
        subprocess.run(["journalctl", "--vacuum-time=1s"],
                       check=False,
                       capture_output=True,
                       timeout=5)
        print("Cleared systemd journal logs")
    except Exception as e:
        print(f"Could not clear journal logs: {e}")
