Feature: Greengrass V2 Machine Learning

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @ML @DLR
  Scenario: I can receive inference results on an MQTT topic after installing aws.greengrass.DLRImageClassification
    Given I subscribe to the following IoT MQTT topics
      | image/classification |
    And I create a Greengrass deployment with components
      | aws.greengrass.DLRImageClassification | LATEST |
    When I update my Greengrass deployment configuration, setting the component aws.greengrass.DLRImageClassification configuration to:
      """
        {
          "MERGE": {
            "accessControl": {
              "aws.greengrass.ipc.mqttproxy": {
                "aws.greengrass.DLRImageClassification:mqttproxy:1": {
                  "policyDescription": "Allows access to publish via topic image/classification.",
                    "operations": ["aws.greengrass#PublishToIoTCore"],
                    "resources": ["${image/classification}"]
                }
              }
            },
            "PublishResultsOnTopic": "${image/classification}",
            "InferenceInterval": "10"
          }
        }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 15 minutes
    And I receive messages on the following IoT MQTT topics after 30 seconds
      | image/classification |