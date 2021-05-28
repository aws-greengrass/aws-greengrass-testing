Feature: Testing Device
  Scenario: Registration and Run

    Given my device is registered as a Thing
    And I start Greengrass
    When I create a test file named "test.txt" in the test directory
    Then the file is created with test information