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
pip install .
```

## How to run the tests?

These tests assume that you have greengrass-lite installled and running.

The tests can be run by executing the following command.

```
pytest -q -s -v --ggTestAccount=<YOUR_AWS_ACCOUNT_NUMBER> --ggTestBucket=<YOUR_TEST_BUCKET> --ggTestRegion=<TEST_REGION> --ggTestThingGroup=<YOUR_THING_GROUP>
```

### How to run a specific test(s)?

pytest provides a `-k` flag which allows the user to select specific test(s)
which they would like to run.

If you want to run a specific test (e.g. `test_Deployment_3_T3`), the command
will be as follows:

```
pytest -q -s -v --ggTestAccount=<YOUR_AWS_ACCOUNT_NUMBER> --ggTestBucket=<YOUR_TEST_BUCKET> --ggTestRegion=<TEST_REGION> --ggTestThingGroup=<YOUR_THING_GROUP> ./src/aws-greengrass-testing-deployment.py  -k "test_Deployment_3_T3"
```

More documentation on the above option can be found
[here](https://docs.pytest.org/en/latest/example/markers.html#using-k-expr-to-select-tests-based-on-their-name).
