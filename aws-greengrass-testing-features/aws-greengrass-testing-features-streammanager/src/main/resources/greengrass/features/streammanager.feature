Feature: Greengrass V2 Stream Manager

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @StreamManager
  Scenario: I can install and run aws.greengrass.StreamManager on my device
    Given I create a random file that is 500KB large, named streammanager-input.log
    When I create an S3 bucket for testing
    And I copy the file streammanager-input.log to my device at ${test.context:installRoot}/streammanager-input.log
    And I create a Greengrass deployment with components
      | com.aws.StreamManagerExport  | classpath:/greengrass/components/recipes/streammanager-component.yaml |
      | aws.greengrass.StreamManager | LATEST |
    And I update my Greengrass deployment configuration, setting the component com.aws.StreamManagerExport configuration to:
      """
        {
          "MERGE": {
            "bucketName": "${aws.resources:s3:bucket:bucketName}",
            "key": "export/streammanager-input.log",
            "inputFile": "file:${test.context:installRoot}/streammanager-input.log"
          }
        }
      """
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 2 minutes
    And the aws.greengrass.StreamManager log on the device contains the line "Stream Manager reporting the state: RUNNING" within 30 seconds
    And the S3 bucket contains the key export/streammanager-input.log within 30 seconds