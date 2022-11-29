## AWS Greengrass Testing Framework

This framework is a collection of building blocks
to support end to end automation from the customer
perspective, using Cucumber as the feature driver. AWS Greengrass uses these very same building
blocks to qualify software changes on variable devices.

## What's inside?

- AWS Resource Management (`aws-greengrass-testing-resources`)
- A platform abstraction over Java Process API
- A bunch of common steps (`aws-greengrass-testing-features`)
- Ability for the features to be driven through IDT.
- Some example use-cases and components (`aws-greengrass-testing-components`)
- Service discovery for extensions like other AWS resources, steps, and configuration.

## How to build?

__Special first time instructions__

This is needed to pull in the latest StreamManger SDK and install them locally for building.

**Unix Based Systems**

From the root of the project run

```
rm -rf ./aws-greengrass-testing-components/aws-greengrass-testing-components-streammanager/lib/streammanager
git submodule update --init
mvn process-resources
```

**Windows**

In order for the `git submodule update --init` command to work on Windows the `core.longpaths` setting
needs to be enabled on git. To do that:

1. Open a command prompt as an Administrator
2. Run `git config --system core.longpaths true`

This is required because Windows has a limit of 260 chars for file names and when pulling the submodules,
the file names are paths that are longer than 260 characters which cause pulling the submodule to fail.

After enabling `core.longpaths` run from the root of the project:

```
rm .\aws-greengrass-testing-components\aws-greengrass-testing-components-streammanager\lib\streammanager
git submodule update --init
mvn process-resources
```

When pulling the submodule succeeds you should be able to find this jar on
`aws-greengrass-testing-components/aws-greengrass-testing-components-streammanager/lib/streammanager/sdk/aws-greengrass-stream-manager-sdk-java.jar`


__Regular compilation instructions__

Replace `compile` with `package` to build shaded jars.

```
mvn clean compile
```

__Run integration tests for the example component__

- Download latest greengrass archive at the example component path
```
curl https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip -o aws-greengrass-testing-examples/aws-greengrass-testing-examples-component/greengrass-nucleus-latest.zip
```

- Get credentials for AWS account. The test needs credentials to identify the AWS account to use and to be able to create
  test resources in the account. [Here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default) 
  is the defaul credential provider chain. The credentials need to made available on the device. Some options are:
  - Copy temporary credentials and set them as env variables
  - Set AWS profile for your environment

- Run the tests
```
mvn clean -DskipTests=false -pl aws-greengrass-testing-examples/aws-greengrass-testing-examples-component -am integration-test
```

- Run mqtt tests
```
mvn clean -DskipITs=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-mqtt/ -am integration-test
```

- Run cloud component tests
```
mvn clean -DskipTests=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-cloudcomponent/ -am integration-test
```

__Running tests with HSM configuration__

Any test can be run with HSM configuration. HSM configuration parameters are defined [here](src/main/java/com/aws/greengrass/testing/modules/HsmParameters.java)
If *ggc.hsm.configured* is set to true, then the framework expects the other HSM parameters to be configured. When 
hsm is configured, [this](src/main/resources/nucleus/configs/basic_hsm_config.yaml) initial config file is used to start
the greengrass with. Before you run the tests with HSM, following steps are pre-requisite:
1. DUT has the HSM installed.
2. A device certificate is created and added to the HSM along with private key. You specify the certificate ARN and 
hsm labels for key and cert as parameters.
3. PKCS library is present either on the host or on the DUT. If the library is already on the DUT, specify the library 
path with a prefix *"dut:"*
4. Similarly, the pkcs plugin jar can be on the host agent or on the DUT. If it is already on DUT, specify the path 
  with the prefix *"dut:"*

__Debugging test failures__

The test logs path is set using the "test.log.path" property in the project. The default value for this will be
"testResults". Thus, by default logs for example component test run will be found at `aws-greengrass-testing-examples/aws-greengrass-testing-examples-component/testResults`

__Fixing licenses__

```
mvn license:format
```


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

