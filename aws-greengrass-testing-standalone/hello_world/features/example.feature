Feature: Testing Device
  @HelloWorld
  Scenario: Registration and Run
    Given my device is registered as a Thing
    And my device is running Greengrass
    When I create a Greengrass deployment with components
      | com.example.HelloWorld | file:hello_world/recipe.yaml |
    And I deploy the Greengrass deployment configuration
    Then the Greengrass deployment is COMPLETED on the device after 60 seconds
    And the com.example.HelloWorld log on the device contains the line "Hello, world" within 10 seconds
