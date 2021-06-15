Feature: Testing a Java Component

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: Installing a dumb component and using IPC
    When I create a Greengrass deployment with components
      | com.aws.PingPong | LATEST |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 30 seconds