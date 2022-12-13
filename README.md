## AWS Greengrass Testing Framework

This framework is a collection of building blocks
that supports end to end automation from the customer
perspective using Cucumber as the feature driver. AWS Greengrass uses the same building
blocks to qualify software changes on various devices.

## What's inside?

- AWS Resource Management (`aws-greengrass-testing-resources`)
- A platform abstraction over Java Process API
- A bunch of common steps (`aws-greengrass-testing-features`)
- The ability for features to be driven through IDT.
- Example use-cases and components (`aws-greengrass-testing-components`)
- Service discovery for extensions such as other AWS resources, steps, and configuration.

## How to build?

__First time instructions__

Install the latest StreamManger SDK locally.


**Unix Based Systems**

If the directory `./aws-greengrass-testing-components/aws-greengrass-testing-components-streammanager/lib/streammanager` exists in your project, remove it by running the following command:

```
rm -rf ./aws-greengrass-testing-components/aws-greengrass-testing-components-streammanager/lib/streammanager
```

This makes sure that when you run the following commands you get a fresh version of the project's submodules.


From the root of the project run:

```
git submodule update --init
mvn process-resources
```

**Windows**

For the `git submodule update --init` command to work on Windows the `core.longpaths` setting
must be enabled on git. To do that:

1. Open a command prompt as Administrator
2. Run `git config --global core.longpaths true`

Windows has a limit of 260 chars for file names. When pulling the submodules,
the file names have paths that are longer than 260 characters, which causes pulling the submodule to fail.

Note:

After enabling `core.longpaths`, if the directory `.\aws-greengrass-testing-components\aws-greengrass-testing-components-streammanager\lib\streammanager`  exists in your project, remove it by running the following command, or delete the folder using the UI.

```
rm .\aws-greengrass-testing-components\aws-greengrass-testing-components-streammanager\lib\streammanager
```

This makes sure that when you run the following commands you get a fresh version of the project's submodules.

From the root of the project run:


```
git submodule update --init
mvn process-resources
```

After the command succeeds you should be able to find this jar
`aws-greengrass-testing-components/aws-greengrass-testing-components-streammanager/lib/streammanager/sdk/aws-greengrass-stream-manager-sdk-java.jar`


__Regular compilation instructions__

Replace `compile` with `package` to build shared jars.

```
mvn clean compile
```

__Run integration tests__

- Download the latest Greengrass archive at the example component path:
```
curl https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip -o aws-greengrass-testing-examples/aws-greengrass-testing-examples-component/greengrass-nucleus-latest.zip
```

- Get credentials for an AWS account. The test needs credentials to identify the AWS account to use and to create
  test resources in the account. For instructions, see [Set default credentials and Region](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup.html#setup-credentials). The credentials must be available on the device. Some options are:
  - Copy temporary credentials and set them as env variables
  - Set the AWS profile for your environment


- Configure user on Windows device
    - Open the Windows Command Prompt (cmd.exe) as an administrator.
    - Create user in the LocalSystem account on the Windows device. Replace **user-name** with the same name of your current system user. Replace **password** with a secure password.
      - ```net user /add user-name password```
    - Download and install the [PsExec utility](https://learn.microsoft.com/en-us/sysinternals/downloads/psexec) from Microsoft on the device.
    - Use the PsExec utility to store the user name and password in the Credential Manager instance for the LocalSystem account.
      - Run the following command. Replace user-name and password with the user's user name and password that you set earlier.
        - ```psexec -s cmd /c cmdkey /generic:user-name /user:user-name /pass:password```
      - If the **PsExec License Agreement** opens, choose **Accept** to agree to the license and run the command.
      


- Run the integration tests (For Window device, open the Windows Command Prompt (cmd.exe) as an administrator to run below tests):

  - Example component tests: 
    - ```mvn clean -DskipTests=false -pl aws-greengrass-testing-examples/aws-greengrass-testing-examples-component -am integration-test```
  - MQTT tests: `
    - ```mvn clean -DskipITs=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-mqtt/ -am integration-test```
  - Cloud component tests: 
    - ```mvn clean -DskipTests=false -pl aws-greengrass-testing-features/aws-greengrass-testing-features-cloudcomponent/ -am integration-test```

__Running tests with an HSM__

Any test can be run with an HSM configuration. HSM configuration parameters are defined in the [HsmParameters.java](aws-greengrass-testing-features/aws-greengrass-testing-features-api/src/main/java/com/aws/greengrass/testing/modules/HsmParameters.java) file.
If *ggc.hsm.configured* is set to true, you must configure the other HSM parameters. When
an HSM is configured, [this initial config file](aws-greengrass-testing-features/aws-greengrass-testing-features-api/src/main/resources/nucleus/configs/basic_hsm_config.yaml) is used to start
Greengrass. Before you run tests with an HSM, following is required:
1. Install the HSM on the DUT.
2. Create and add a device certificate to the HSM, along with a private key. You specify the certificate ARN and
   HSM labels for the key and cert as parameters.
3. Install the PKCS library on the host or the DUT. If the library is on the DUT, specify the library
   path with a prefix *"dut:"*
4. Similarly, the PKCS plugin jar can be on the host agent or on the DUT. If it is on the DUT, specify the path
   with the prefix *"dut:"*

__Debugging test failures__

Set the test log path using the "test.log.path" property in the project. The default value is
"testResults". By default, logs for the example component test run are found at `aws-greengrass-testing-examples/aws-greengrass-testing-examples-component/testResults`

__Fixing licenses__

```
mvn license:format
```


## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

## Getting Help
GitHub [issue](https://github.com/aws-greengrass/aws-greengrass-testing/issues) is the preferred channel to interact with our team.

## Resources

- [AWS Greengrass Testing Framework Home Page](https://github.com/aws-greengrass/aws-greengrass-testing/wiki/AWS-Greengrass-Testing-Framework-Home-Page)
- [How to build custom test cases with AWS Greengrass Testing Framework](https://github.com/aws-greengrass/aws-greengrass-testing/wiki/How-to-build-custom-test-cases-with-AWS-Greengrass-Testing-Framework)
- [How to run test cases with AWS Greengrass Testing Framework locally](https://github.com/aws-greengrass/aws-greengrass-testing/wiki/How-to-run-test-cases-with-AWS-Greengrass-Testing-Framework-locally)

