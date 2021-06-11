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

  @MQTT
  Scenario: Roundtrip IoT MQTT
    Given I connect an MQTT client to IoT
    And I subscribe to the following IoT MQTT topics
      | my/topic/a |
      | my/topic/b |
    When I publish messages on the following IoT MQTT topics
      | my/topic/a | This is a test       |
      | my/topic/b | This is another test |
    Then I receive messages on the following IoT MQTT topics after 30 seconds
      | my/topic/a | test |
      | my/topic/b | test |