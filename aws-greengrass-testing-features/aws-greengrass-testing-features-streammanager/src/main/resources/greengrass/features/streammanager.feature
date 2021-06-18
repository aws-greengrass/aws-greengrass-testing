Feature: Greengrass V2 Stream Manager

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  @StreamManager
  Scenario: I can install and run aws.greengrass.StreamManager on my device.
    Given I create a Greengrass deployment with components
      | aws.greengrass.StreamManager | LATEST |
    When I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 2 minutes
    And the aws.greengrass.StreamManager log on the device contains the line "Stream Manager reporting the state: RUNNING" within 30 seconds