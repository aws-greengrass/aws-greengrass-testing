Feature: Testing a Java Component

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: Installing a basic component and using IPC
    When I create a Greengrass deployment with components
      | com.aws.PingPong | LATEST |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 30 seconds
    And the com.aws.PingPong log on the device contains the line "Connected to IPC" within 20 seconds
    And the com.aws.PingPong log on the device contains the line "Received Pong from Ping" within 10 seconds
    And the com.aws.PingPong log on the device contains the line "Received Ping from Pong" within 10 seconds