### Installing python3 venv

Following is the command to setup python3 venv environment

```shell
sudo apt install python3-venv
python3 -m venv env
. ./env/bin/activate
```

You will now be switched to the virtual environment.

Run the following command to install all the required libraries to run the test
suite.

```shell
pip install -r requirements.txt
```

## How to run the tests?

These tests assume that you have greengrass-lite installled and running.

The tests can be run by executing the following command

```
pytest -q -s  --ggTestAccount=<YOUR_AWS_ACCOUNT_NUMBER> --ggTestBucket=<YOUR_TEST_BUCKET> --ggTestRegion=<TEST_REGION> --ggTestThingGroup=<YOUR_THING_GROUP> greengrassTestSuite.py
```
