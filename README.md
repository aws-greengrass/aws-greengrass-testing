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

```
mvn -DskipTests=false -pl aws-greengrass-testing-examples/aws-greengrass-testing-examples-component -am integration-tests
```

__Fixing licenses__

```
mvn license:format
```


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

