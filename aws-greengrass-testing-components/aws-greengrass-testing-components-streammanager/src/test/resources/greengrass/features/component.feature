Feature: Greengrass V2 Stream Manager Component Integration

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: I can install the testing Stream Manager integration component
    Given I create a random file that is 500KB large, named streammanager-input.log
    When I create an S3 bucket for testing
    And I copy the file streammanager-input.log to my device at ${test.context:installRoot}/streammanager-input.log
    And I create a Greengrass deployment with components
      | com.aws.StreamManagerExport  | LATEST |
      | aws.greengrass.StreamManager | ^2.0.0 |
    And I update my Greengrass deployment configuration, setting the component com.aws.StreamManagerExport configuration to:
      """
        {
          "MERGE": {
            "bucketName": "${aws.resources:s3:bucket:bucketName}",
            "key": "export/input.log",
            "inputFile": "file:${test.context:installRoot}/streammanager-input.log"
          }
        }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 5 minutes
    And the S3 bucket contains the key export/input.log within 30 seconds