Feature: Example feature
  Scenario: First scenario
    Given I have created the test directory
    When I create a test file named "test" in the test directory
    Then the file is created with test information

  Scenario: Second scenario
    Given I have created the test directory
    When I create a test file named "another-test" in the test directory
    Then the file is created with test information