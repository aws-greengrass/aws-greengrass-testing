Feature: Testing Device
  Scenario: Registration and Run

    Given my device is registered as a Thing
    And my device is running Greengrass
    And I have created the test directory
    When I create a Greengrass deployment with components
      | com.example.HelloWorld | classpath:/greengrass/components/hello_world.yaml |
    And I create a test file named test in the test directory
    Then the file is created with test information
    And the Greengrass deployment is COMPLETED on the device after 60 seconds