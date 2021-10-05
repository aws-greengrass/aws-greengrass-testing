Feature: Testing local deployment using CLI in Greengrass

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: A component is deployed locally using CLI
    When I create a Greengrass deployment with components
      | aws.greengrass.Cli | 2.4.0 |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 180 seconds
    Then I verify greengrass-cli is available in greengrass root
    When I create a local deployment with components
      | aws.greengrass.LocalHelloWorld | local:localcomponent/recipes/local_hello_world.yaml |
    Then the local Greengrass deployment is SUCCEEDED on the device after 120 seconds
    And the aws.greengrass.LocalHelloWorld log on the device contains the line "Hello World!!" within 20 seconds

