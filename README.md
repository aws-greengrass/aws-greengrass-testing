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

```
git submodule update --init
mvn process-resources
```

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
mvn -DskipTests=false -pl aws-greengrass-testing-examples/aws-greengrass-testing-examples-component -am integration-test
```

- Run mqtt tests
```
mvn clean -DskipTests=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-mqtt/ -am integration-test
```

- Run cloud component tests
```
mvn clean -DskipTests=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-cloudcomponent/ -am integration-test
```

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

