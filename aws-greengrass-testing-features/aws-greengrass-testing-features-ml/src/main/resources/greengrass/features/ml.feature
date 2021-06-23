Feature: Greengrass V2 Machine Learning

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @ML @DLR
  Scenario: I can receive inference results after installing aws.greengrass.DLRImageClassification
    Given I create a Greengrass deployment with components
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
                    "resources": ["dlr/${test.id}/image/classification"]
                }
              }
            },
            "PublishResultsOnTopic": "dlr/${test.id}/image/classification",
            "InferenceInterval": "10"
          }
        }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.DLRImageClassification log on the device contains the line "image-classification" within 30 seconds

  @ML @TensorFlow
  Scenario: I can receive image inference results after installing aws.greengrass.TensorFlowLiteImageClassification
    Given I create a Greengrass deployment with components
      | aws.greengrass.TensorFlowLiteImageClassification | LATEST |
    When I update my Greengrass deployment configuration, setting the component aws.greengrass.TensorFlowLiteImageClassification configuration to:
      """
        {
          "MERGE": {
            "accessControl": {
              "aws.greengrass.ipc.mqttproxy": {
                "aws.greengrass.TensorFlowLiteImageClassification:mqttproxy:1": {
                  "policyDescription": "Allows access to publish via topic image/classification.",
                    "operations": ["aws.greengrass#PublishToIoTCore"],
                    "resources": ["tensorflow/${test.id}/image/classification"]
                }
              }
            },
            "PublishResultsOnTopic": "tensorflow/${test.id}/image/classification",
            "InferenceInterval": "10"
          }
        }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.TensorFlowLiteImageClassification log on the device contains the line "image-classification" within 30 seconds

  @ML @SageMakerEdgeManager
  Scenario: I can install SageMaker Edge Manager agent using aws.greengrass.SageMakerEdgeManager component
    When I create a Greengrass deployment with components
      | aws.greengrass.SageMakerEdgeManager | LATEST |
    And I create an S3 bucket for testing
    And I update my Greengrass deployment configuration, setting the component aws.greengrass.SageMakerEdgeManager configuration to:
      """
      {
        "MERGE": {
          "DeviceFleetName": "some-fleet",
          "BucketName": "${aws.resources:s3:bucket:bucketName}"
        }
      }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the aws.greengrass.SageMakerEdgeManager log on the device contains the line "Server listening on unix:///tmp/aws.greengrass.SageMakerEdgeManager" within 60 seconds
    And the aws.greengrass.SageMakerEdgeManager log on the device not contains the line "GetBucketLocation failed"