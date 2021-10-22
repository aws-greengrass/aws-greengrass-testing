Feature: Testing Secret manager in Greengrass

  Background:
    Given my device is registered as a Thing
    And my device is running Greengrass

  Scenario: Component publishes MQTT message to Iot core and retrieves it as well

    When I create a Greengrass deployment with components
      | aws.greengrass.secretComponents | classpath:/greengrass/components/recipes/secret.yaml |
    And I create a secret named EGUAT with value password
    And I deploy the Greengrass deployment configuration

    Then the Greengrass deployment is COMPLETED on the device after 180 seconds

