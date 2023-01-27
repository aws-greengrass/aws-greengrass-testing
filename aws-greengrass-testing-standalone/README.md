## AWS Greengrass Testing Application

This is a standalone application to drive "feature testing" outside the
context of a Java programming environment.

## Run custom features

Let's assume that you are building a Python based component like the
[HelloWorld][1]. You will need a minimum:

1. The component recipe file
2. The component source
3. Some version of the [Greengrass archive][2] downloaded as `greengrass-nucleus-latest.zip`

Assuming the following directory structure [hello_world](hello_world):

- [recipe.yaml](hello_world/recipe.yaml)
- [src/main.py](hello_world/src/main.py)
- [features/example.feature](hello_world/features/example.feature)

You can now run the end to end feature:

```
java \
-Dggc.archive=greengrass-nucleus-latest.zip \
-Dfeature.path=hello_world/features \
-Dtest.log.path=hello_world/test-results \
-Dtags=HelloWorld \
-jar aws-greengrass-testing-standlone.jar
```

The results of the run can be found in `hello_world/test-results`.

## Run provided features
You can also run features provided by this test framework. Here are the provided test features: 
- Basic test features:
  - [cloudComponent.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-cloudcomponent/src/main/resources/greengrass/features/cloudComponent.feature): Validates device capability for cloud components. 
  - [localdeployment.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-localdeployment/src/main/resources/greengrass/features/localdeployment.feature): Validates device capability for local components.
  - [mqtt.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-mqtt/src/main/resources/greengrass/features/mqtt.feature): Validates that the device can subscribe and publish to AWS IoT Core MQTT topics.



- Below test features are only supported for Linux-based Greengrass core devices.
  - [streammanager.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-streammanager/src/main/resources/greengrass/features/streammanager.feature): Validates that the device can download, install, and run the AWS IoT Greengrass stream manager.
  - [docker.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-docker/src/main/resources/greengrass/features/docker.feature): Validates that the device can download a Docker container image from Amazon ECR.
  - [ml.feature](../aws-greengrass-testing-features/aws-greengrass-testing-features-ml/src/main/resources/greengrass/features/ml.feature): Validates that the device can perform ML inference using the Deep Learning Runtime and TensorFlow Lite ML frameworks.

Here is an example on running the end to end test for OTFStable tag:


```
java \
-Dggc.archive=greengrass-nucleus-latest.zip \
-Dtags=OTFStable
-jar aws-greengrass-testing-standlone.jar
```

There are also additional parameters can be configured when executing the jar:
- [TestLauncherParameters](../aws-greengrass-testing-launcher/src/main/java/com/aws/greengrass/testing/launcher/TestLauncherParameters.java)
- [FeatureParameters](../aws-greengrass-testing-features/aws-greengrass-testing-features-api/src/main/java/com/aws/greengrass/testing/modules/FeatureParameters.java)
- [HsmParameters](../aws-greengrass-testing-features/aws-greengrass-testing-features-api/src/main/java/com/aws/greengrass/testing/modules/HsmParameters.java)
- [ModuleParameters](../aws-greengrass-testing-modules/src/main/java/com/aws/greengrass/testing/modules/ModuleParameters.java)

**Note:** 
- Windows has a path length limitation of 260 characters. Please keep your paths under the 260 character limit when using above parameters.
- For running the aws-greengrass-testing-standlone.jar on Windows devices, you have to configure user credentials. (See *Configure user credentials for Windows devices* section in [README](../README.md))


[1]: https://docs.aws.amazon.com/greengrass/v2/developerguide/create-components.html#develop-component
[2]: https://d2s8p88vqu9w66.cloudfront.net/releases/greengrass-nucleus-latest.zip
