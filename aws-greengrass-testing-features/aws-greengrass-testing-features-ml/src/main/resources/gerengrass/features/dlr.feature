Feature: Greengrass V2 Machine Learning

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @ML @DLR
  Scenario: I can receive inference results on an MQTT topic after installing aws.greengrass.DLRImageClassification
    Given I subscribe to the following IoT MQTT topics
      | image-classification |
    When I create a Greengrass deployment configuration with components
      | aws.greengrass.DLRImageClassification | LATEST |
    And I update my deployment configuration, setting the component aws.greengrass.DLRImageClassification configuration:
      """
        {
          "MERGE": {
            "accessControl": {
              "aws.greengrass.ipc.mqttproxy": {
                "aws.greengrass.DLRImageClassification:mqttproxy:1": {
                  "policyDescription": "Allows access to publish via topic image-classification.",
                    "operations": ["aws.greengrass#PublishToIoTCore"],
                    "resources": ["${image-classification}"]
                }
              }
            },
            "PublishResultsOnTopic": "${image-classification}",
            "InferenceInterval": "10"
          }
        }
      """
    And I deploy the deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 15 minutes